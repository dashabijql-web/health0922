package com.xzkj.hv2.watch.buffer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.xzkj.hv2.watch.WatchMetrics;
import com.xzkj.hv2.watch.WatchProperties;

/**
 * 手表数据的缓冲（docs/03 第五节）。
 * <pre>
 * 有效数据 ──▶ &lt;前缀&gt;buf ──(Lua 一步移走最多一批)──▶ &lt;前缀&gt;processing ──写库成功──▶ 删除
 *                                                              └─ 写不进的单条 ──▶ &lt;前缀&gt;dead
 * </pre>
 * Redis 不可用时降级为进程内的有界内存队列（默认上限 10000 条），满了丢最旧的并计数；
 * 由 {@link BufferFlusher} 照常批量写库，内存队列写完、Redis 恢复后再切回 Redis。
 * 降级期间进程崩溃，内存队列里的数据会丢，这是接受的代价。
 */
@Component
public class WatchBuffer {

    private static final Logger log = LoggerFactory.getLogger(WatchBuffer.class);

    /** "移出 buf"和"放进 processing"一步完成，中间崩溃也不会两边都没有 */
    private static final RedisScript<List> MOVE_BATCH = new DefaultRedisScript<>("""
            local items = redis.call('LRANGE', KEYS[1], 0, tonumber(ARGV[1]) - 1)
            if #items > 0 then
              redis.call('RPUSH', KEYS[2], unpack(items))
              redis.call('LTRIM', KEYS[1], #items, -1)
            end
            return items
            """, List.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String bufKey;
    private final String processingKey;
    private final String deadKey;
    private final int memoryCapacity;
    private final Counter memoryDropped;

    private final Deque<WatchDatum> memory = new ArrayDeque<>();
    private volatile boolean redisMode = true;

    public WatchBuffer(StringRedisTemplate redis, ObjectMapper json, WatchProperties props, WatchMetrics metrics) {
        this.redis = redis;
        this.json = json;
        String prefix = props.buffer().keyPrefix();
        this.bufKey = prefix + "buf";
        this.processingKey = prefix + "processing";
        this.deadKey = prefix + "dead";
        this.memoryCapacity = props.buffer().memoryCapacity();
        this.memoryDropped = metrics.registry().counter("health.buffer.memory.dropped");
    }

    /** 放进缓冲。Redis 写失败时切到内存队列，这一条也放进内存队列。 */
    public void push(WatchDatum d) {
        if (redisMode) {
            try {
                redis.opsForList().rightPush(bufKey, json.writeValueAsString(d));
                return;
            } catch (DataAccessException e) {
                if (redisMode) {
                    redisMode = false;
                    log.warn("Redis 不可用，手表数据改存内存队列（上限 {} 条）：{}", memoryCapacity, e.getMessage());
                }
            }
        }
        pushMemory(d);
    }

    private void pushMemory(WatchDatum d) {
        synchronized (memory) {
            memory.addLast(d);
            while (memory.size() > memoryCapacity) {
                memory.pollFirst();
                memoryDropped.increment();
            }
        }
    }

    public boolean redisMode() {
        return redisMode;
    }

    /** Redis 恢复、内存队列已写完后切回 Redis。 */
    void useRedis() {
        if (!redisMode) {
            redisMode = true;
            log.info("Redis 已恢复，手表数据切回 Redis 缓冲");
        }
    }

    /** 试一下 Redis 能不能用。 */
    boolean redisReachable() {
        try {
            return "PONG".equalsIgnoreCase(redis.execute(c -> c.ping(), true));
        } catch (DataAccessException e) {
            return false;
        }
    }

    // ---- 内存队列 ----

    List<WatchDatum> pollMemory(int max) {
        synchronized (memory) {
            List<WatchDatum> out = new ArrayList<>(Math.min(max, memory.size()));
            while (out.size() < max && !memory.isEmpty()) {
                out.add(memory.pollFirst());
            }
            return out;
        }
    }

    /** 写库失败时把这一批放回队头，保持原来的先后。 */
    void returnToMemory(List<WatchDatum> batch) {
        synchronized (memory) {
            for (int i = batch.size() - 1; i >= 0; i--) {
                memory.addFirst(batch.get(i));
            }
        }
    }

    int memorySize() {
        synchronized (memory) {
            return memory.size();
        }
    }

    // ---- Redis ----

    /** 上一次没写完的一批（进程崩溃、写库失败时留下的）。 */
    List<String> processing() {
        List<String> items = redis.opsForList().range(processingKey, 0, -1);
        return items == null ? List.of() : items;
    }

    @SuppressWarnings("unchecked")
    List<String> moveBatch(int max) {
        List<String> items = redis.execute(MOVE_BATCH, List.of(bufKey, processingKey), String.valueOf(max));
        return items == null ? List.of() : items;
    }

    void clearProcessing() {
        redis.delete(processingKey);
    }

    long redisBacklog() {
        Long buf = redis.opsForList().size(bufKey);
        Long processing = redis.opsForList().size(processingKey);
        return (buf == null ? 0 : buf) + (processing == null ? 0 : processing);
    }

    /** 写不进库的单条放进死信，人工排查；Redis 也不可用时只能记日志。 */
    void dead(Collection<String> rawItems) {
        if (rawItems.isEmpty()) {
            return;
        }
        try {
            redis.opsForList().rightPushAll(deadKey, rawItems);
        } catch (DataAccessException e) {
            log.error("{} 条写不进库的数据无法放进死信（Redis 不可用），已丢弃", rawItems.size());
        }
    }

    String serialize(WatchDatum d) {
        return json.writeValueAsString(d);
    }

    /** 解析失败返回 null（死信处理）。 */
    WatchDatum parse(String raw) {
        try {
            return json.readValue(raw, WatchDatum.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}
