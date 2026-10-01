package com.xzkj.hv2.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用本机 Redis 验证 Lua 脚本的过期逻辑。Redis 不可用时跳过。
 * 只用 hv2:test:auth:fail: 开头的随机 key，测试结束自己删掉，不影响其他数据。
 */
class RedisLoginFailureCounterTest {

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static boolean available;

    private RedisLoginFailureCounter counter;
    private String username;

    @BeforeAll
    static void connect() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6380"));
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        try {
            available = "PONG".equals(factory.getConnection().ping());
        } catch (RuntimeException e) {
            available = false;
        }
    }

    @AfterAll
    static void close() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(available, "本机 Redis 不可用，跳过");
        counter = new RedisLoginFailureCounter(redis,
                new AuthProperties(5, Duration.ofMinutes(10), "hv2:test:auth:fail:"));
        username = "u-" + UUID.randomUUID();
    }

    @AfterEach
    void cleanUp() {
        if (available && counter != null) {
            redis.delete(counter.key(username));
        }
    }

    private long ttlSeconds() {
        Long ttl = redis.getExpire(counter.key(username), TimeUnit.SECONDS);
        return ttl == null ? -2 : ttl;
    }

    @Test
    void firstFailureSetsTenMinuteExpiry() {
        assertThat(counter.current(username)).isZero();
        assertThat(counter.increment(username)).isEqualTo(1);
        assertThat(counter.key(username)).startsWith("hv2:");
        assertThat(ttlSeconds()).isBetween(595L, 600L);
    }

    @Test
    void laterFailuresKeepOriginalExpiryUntilFifthResetsIt() {
        counter.increment(username);
        // 模拟第一次失败已过去 9 分钟：只剩 60 秒
        redis.expire(counter.key(username), Duration.ofSeconds(60));

        counter.increment(username);
        counter.increment(username);
        counter.increment(username);
        assertThat(ttlSeconds()).isLessThanOrEqualTo(60);

        assertThat(counter.increment(username)).isEqualTo(5);
        assertThat(ttlSeconds()).isBetween(595L, 600L);
        assertThat(counter.current(username)).isEqualTo(5);
    }

    @Test
    void keyWithoutExpiryGetsOne() {
        redis.opsForValue().set(counter.key(username), "2");

        counter.increment(username);

        assertThat(ttlSeconds()).isBetween(595L, 600L);
    }

    @Test
    void clearRemovesKey() {
        counter.increment(username);
        counter.clear(username);

        assertThat(redis.hasKey(counter.key(username))).isFalse();
        assertThat(counter.current(username)).isZero();
    }
}
