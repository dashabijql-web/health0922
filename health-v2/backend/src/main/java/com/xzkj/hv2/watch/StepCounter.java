package com.xzkj.hv2.watch;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.xzkj.hv2.common.config.TimeConfig;
import com.xzkj.hv2.watch.buffer.WatchBuffer;

/**
 * 今天的步数（docs/03 第五节"步数"）。AP03 里的步数是手表上的累计计数器，不按天归零，到上限后从 0 重新计。
 * 今天的步数 = 今天各次读数的增量之和，增量在收到心跳时就算好：
 * <pre>
 * 没有上次读数（新表、首次上线、状态丢失）   0，只记下这次读数作起点
 * 卡编码和上次不同（换绑）                   0，重新作起点
 * 这次读数时间早于上次（乱序）                忽略这次读数
 * 新读数 ≥ 上次                             差值
 * 新读数 &lt; 上次（计数器回绕或手表重启）       新读数本身
 * </pre>
 * 跨天：0 点后第一次读数，"今天累计"从 0 开始，但"上次读数"保留，0 点前最后一次读数到这次之间的步数算进新的一天。
 * <p>
 * 状态存在 Redis 的 &lt;前缀&gt;step:&lt;imei&gt;，用 Lua 脚本原子更新。Redis 里没有这个人的状态时
 * （新表、Redis 丢了数据、换绑），从 STEP_DAILY 取这个人最近一天的记录作起点，所以当天步数不会因此变少：
 * 今天已有的累计照样接上；上次读数只在是同一块表时才用。Redis 不可用时改用进程内的状态，算法相同。
 */
@Component
public class StepCounter {

    private static final Logger log = LoggerFactory.getLogger(StepCounter.class);

    /** 状态保留 3 天：跨天要用前一天最后的读数 */
    private static final long STATE_TTL_SECONDS = 3 * 24 * 3600;
    static final long NO_STATE = -2;
    static final long OUT_OF_ORDER = -1;

    /**
     * KEYS[1] 状态；ARGV：读数、时间(毫秒)、卡编码、日期、有效期、是否带起点、起点读数、起点时间、起点今日累计、起点日期。
     * 返回今天累计；-1 乱序忽略；-2 没有这个人的状态，需要带上起点再调一次。
     */
    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local raw = tonumber(ARGV[1])
            local at = tonumber(ARGV[2])
            local card = ARGV[3]
            local date = ARGV[4]
            local s = redis.call('HMGET', KEYS[1], 'raw', 'at', 'card', 'date', 'today')
            local lastRaw, lastAt, lastDate, today
            if s[3] == card then
              lastRaw = tonumber(s[1]); lastAt = tonumber(s[2]); lastDate = s[4]; today = tonumber(s[5])
            elseif ARGV[6] == '1' then
              if ARGV[7] ~= '' then lastRaw = tonumber(ARGV[7]); lastAt = tonumber(ARGV[8]) end
              today = tonumber(ARGV[9]); lastDate = ARGV[10]
            else
              return -2
            end
            if lastAt and at < lastAt then return -1 end
            if lastDate ~= date then today = 0 end
            if lastRaw then
              if raw >= lastRaw then today = today + (raw - lastRaw) else today = today + raw end
            end
            redis.call('HSET', KEYS[1], 'raw', ARGV[1], 'at', ARGV[2], 'card', card, 'date', date,
                       'today', string.format('%d', today))
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
            return today
            """, Long.class);

    private final StringRedisTemplate redis;
    private final WatchBuffer buffer;
    private final WatchMapper mapper;
    private final String keyPrefix;
    /** Redis 不可用时的状态 */
    private final Map<String, State> memory = new ConcurrentHashMap<>();

    public StepCounter(StringRedisTemplate redis, WatchBuffer buffer, WatchMapper mapper, WatchProperties props) {
        this.redis = redis;
        this.buffer = buffer;
        this.mapper = mapper;
        this.keyPrefix = props.buffer().keyPrefix() + "step:";
    }

    /**
     * 收到一次计数器读数。
     *
     * @return 今天的累计步数；乱序被忽略时为 null
     */
    public Long count(String imei, String card, long raw, long atMillis) {
        LocalDate date = dateOf(atMillis);
        if (buffer.redisMode()) {
            try {
                Long today = viaRedis(imei, card, raw, atMillis, date);
                memory.remove(imei);
                return today;
            } catch (DataAccessException e) {
                log.warn("步数状态 Redis 不可用，改用进程内状态：{}", e.getMessage());
            }
        }
        return viaMemory(imei, card, raw, atMillis, date);
    }

    private Long viaRedis(String imei, String card, long raw, long at, LocalDate date) {
        List<String> key = List.of(keyPrefix + imei);
        String[] args = {String.valueOf(raw), String.valueOf(at), card, date.toString(),
                String.valueOf(STATE_TTL_SECONDS), "0", "", "", "", ""};
        Long r = redis.execute(SCRIPT, key, (Object[]) args);
        if (r != null && r == NO_STATE) {
            State seed = seed(imei, card, date);
            args[5] = "1";
            args[6] = seed.lastRaw == null ? "" : String.valueOf(seed.lastRaw);
            args[7] = seed.lastRaw == null ? "" : String.valueOf(seed.lastAt);
            args[8] = String.valueOf(seed.today);
            args[9] = seed.date.toString();
            r = redis.execute(SCRIPT, key, (Object[]) args);
        }
        return r == null || r < 0 ? null : r;
    }

    private Long viaMemory(String imei, String card, long raw, long at, LocalDate date) {
        State current = memory.get(imei);
        State seed = current != null && card.equals(current.card) ? null : seed(imei, card, date);
        long[] result = new long[1];
        memory.compute(imei, (k, s) -> {
            State base = s != null && card.equals(s.card) ? s : seed;
            if (base == null) {
                // 别的线程刚好换了状态：本次按乱序处理，丢这一次读数
                result[0] = OUT_OF_ORDER;
                return s;
            }
            Step step = apply(base, raw, at, card, date);
            result[0] = step.today;
            return step.next == null ? s : step.next;
        });
        return result[0] < 0 ? null : result[0];
    }

    /** 从 STEP_DAILY 找起点：今天已有的累计接着算；上次读数只在是同一块表时才用。 */
    private State seed(String imei, String card, LocalDate date) {
        WatchMapper.StepSeed row = mapper.selectStepSeed(card, date);
        if (row == null) {
            return new State(card, null, 0, date, 0);
        }
        long today = row.statDate().equals(date) ? row.steps() : 0;
        if (!imei.equals(row.deviceImei())) {
            return new State(card, null, 0, date, today);
        }
        long lastAt = row.updatedAt().atZone(TimeConfig.ZONE).toInstant().toEpochMilli();
        return new State(card, row.lastRaw(), lastAt, date, today);
    }

    /** 和 Lua 脚本相同的算法（Redis 不可用时用；测试里两边跑同一组用例）。 */
    static Step apply(State s, long raw, long at, String card, LocalDate date) {
        if (s.lastRaw != null && at < s.lastAt) {
            return new Step(OUT_OF_ORDER, null);
        }
        long today = s.date.equals(date) ? s.today : 0;
        if (s.lastRaw != null) {
            today += raw >= s.lastRaw ? raw - s.lastRaw : raw;
        }
        return new Step(today, new State(card, raw, at, date, today));
    }

    static LocalDate dateOf(long millis) {
        return Instant.ofEpochMilli(millis).atZone(TimeConfig.ZONE).toLocalDate();
    }

    record State(String card, Long lastRaw, long lastAt, LocalDate date, long today) {
    }

    record Step(long today, State next) {
    }
}
