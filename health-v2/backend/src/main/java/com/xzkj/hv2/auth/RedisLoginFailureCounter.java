package com.xzkj.hv2.auth;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 失败次数存在 Redis 的 hv2:auth:fail:&lt;用户名&gt;（docs/05 第八节）：
 * <ul>
 *   <li>第 1 次失败时设过期时间（默认 10 分钟），窗口内不到上限就自动清零；</li>
 *   <li>达到上限（第 5 次）时把过期时间重新设为 10 分钟，保证每次锁满 10 分钟；</li>
 *   <li>登录成功删掉这个 key。到期自动解除，后端重启不影响。</li>
 * </ul>
 * 加 1 和设过期用一段 Lua 脚本在 Redis 里一次完成，两个请求同时失败也不会漏设过期时间。
 * Redis 不可用时不阻断登录（锁定暂时失效），只记警告日志。
 */
@Component
public class RedisLoginFailureCounter implements LoginFailureCounter {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginFailureCounter.class);

    private static final RedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or count == tonumber(ARGV[2]) or redis.call('PTTL', KEYS[1]) < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AuthProperties props;

    public RedisLoginFailureCounter(StringRedisTemplate redis, AuthProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public long current(String username) {
        try {
            String value = redis.opsForValue().get(key(username));
            return value == null ? 0 : Long.parseLong(value);
        } catch (DataAccessException e) {
            log.warn("读取登录失败次数时 Redis 不可用，本次不做锁定检查: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public long increment(String username) {
        try {
            Long count = redis.execute(INCREMENT_SCRIPT, List.of(key(username)),
                    String.valueOf(props.lockDuration().toMillis()),
                    String.valueOf(props.maxFailures()));
            return count == null ? 0 : count;
        } catch (DataAccessException e) {
            log.warn("记录登录失败次数时 Redis 不可用: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void clear(String username) {
        try {
            redis.delete(key(username));
        } catch (DataAccessException e) {
            log.warn("清除登录失败次数时 Redis 不可用: {}", e.getMessage());
        }
    }

    String key(String username) {
        return props.failKeyPrefix() + username;
    }
}
