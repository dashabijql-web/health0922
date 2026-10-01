package com.xzkj.hv2.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

import com.xzkj.hv2.common.config.TimeConfig;
import com.xzkj.hv2.watch.buffer.WatchBuffer;
import com.xzkj.hv2.watch.buffer.WatchDatum;

/**
 * 步数（docs/03 第五节）：同一组用例分别走 Redis 的 Lua 脚本和 Redis 不可用时的进程内状态，结果必须一样。
 * 覆盖计数器回绕、手表重启、跨过 0 点、首次上线、换绑、乱序、Redis 丢了状态：当天步数不减少、没有巨大跳变。
 * 只用 hv2:test:step: 开头的 key，测试完自己删掉。本机 Redis 不可用时 Redis 那一半跳过。
 */
class StepCounterTest {

    private static final String PREFIX = "hv2:test:step-counter:";
    private static final String IMEI = "869900000000001";
    private static final String IMEI2 = "869900000000002";
    private static final String A = "62082300920390001";
    private static final String B = "62082300920390002";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 23, 8, 0);

    private static LettuceConnectionFactory good;
    private static LettuceConnectionFactory dead;
    private static boolean redisUp;

    private final Map<String, WatchMapper.StepSeed> stepDaily = new HashMap<>();
    private StepCounter counter;
    private StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        // 本项目的 Redis 容器 hv2-redis（AGENTS.md"环境"），可用环境变量 REDIS_PORT 改
        good = factory(Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6380")));
        try {
            redisUp = "PONG".equals(good.getConnection().ping());
        } catch (RuntimeException e) {
            redisUp = false;
        }
        // 没有服务监听的端口：模拟 Redis 不可用
        dead = factory(1);
    }

    @AfterAll
    static void close() {
        good.destroy();
        dead.destroy();
    }

    private static LettuceConnectionFactory factory(int port) {
        LettuceConnectionFactory f = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).build());
        f.afterPropertiesSet();
        f.start();
        return f;
    }

    private void setUp(boolean useRedis) {
        if (useRedis) {
            assumeTrue(redisUp, "本机 Redis 不可用，跳过");
        }
        redis = new StringRedisTemplate(useRedis ? good : dead);
        WatchProperties props = new WatchProperties(false, 0, 10, 20, 12, 60, Duration.ofSeconds(30),
                Duration.ofMinutes(10), 8192, 60, 1, 10,
                new WatchProperties.Buffer(false, PREFIX, 5000, 1000, 100, 3, Duration.ofSeconds(1)));
        WatchBuffer buffer = new WatchBuffer(redis, JsonMapper.builder().build(), props,
                new WatchMetrics(new SimpleMeterRegistry()));
        if (!useRedis) {
            // 写一次失败，缓冲就切到内存模式，步数也跟着改用进程内状态
            buffer.push(WatchDatum.heartbeat(1, IMEI, A, 1, 80, null, null));
            assertThat(buffer.redisMode()).isFalse();
        } else {
            cleanKeys();
        }
        counter = new StepCounter(redis, buffer, new FakeStepDaily(), props);
    }

    private void cleanKeys() {
        Set<String> keys = redis.keys(PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private Long read(String imei, String card, long raw, LocalDateTime at) {
        Long today = counter.count(imei, card, raw, at.atZone(TimeConfig.ZONE).toInstant().toEpochMilli());
        if (today != null) {
            // 写库时覆盖 STEP_DAILY（只接受更晚的读数）；这里模拟写库后的结果，供"状态丢失"时取起点
            WatchMapper.StepSeed old = stepDaily.get(card + at.toLocalDate());
            if (old == null || old.updatedAt().isBefore(at)) {
                stepDaily.put(card + at.toLocalDate(), new WatchMapper.StepSeed(at.toLocalDate(), today, raw, at, imei));
            }
        }
        return today;
    }

    @ParameterizedTest(name = "redis={0}")
    @ValueSource(booleans = {true, false})
    void firstOnlineCountsFromZeroThenAddsDifferences(boolean useRedis) {
        setUp(useRedis);
        assertThat(read(IMEI, A, 5000, T0)).as("首次上线只记起点").isZero();
        assertThat(read(IMEI, A, 5120, T0.plusMinutes(5))).isEqualTo(120);
        assertThat(read(IMEI, A, 5200, T0.plusMinutes(10))).isEqualTo(200);
        cleanUp(useRedis);
    }

    @ParameterizedTest(name = "redis={0}")
    @ValueSource(booleans = {true, false})
    void wrapAroundAndRestartAddTheNewReading(boolean useRedis) {
        setUp(useRedis);
        read(IMEI, A, 65500, T0);
        assertThat(read(IMEI, A, 65530, T0.plusMinutes(1))).isEqualTo(30);
        assertThat(read(IMEI, A, 20, T0.plusMinutes(2))).as("计数器回绕：加新读数本身").isEqualTo(50);
        assertThat(read(IMEI, A, 80, T0.plusMinutes(3))).isEqualTo(110);
        assertThat(read(IMEI, A, 5, T0.plusMinutes(4))).as("手表重启归零").isEqualTo(115);
        cleanUp(useRedis);
    }

    @ParameterizedTest(name = "redis={0}")
    @ValueSource(booleans = {true, false})
    void outOfOrderReadingIsIgnored(boolean useRedis) {
        setUp(useRedis);
        read(IMEI, A, 1000, T0);
        read(IMEI, A, 1100, T0.plusMinutes(5));
        assertThat(read(IMEI, A, 1050, T0.plusMinutes(3))).as("比上次早的读数").isNull();
        assertThat(read(IMEI, A, 1150, T0.plusMinutes(6))).isEqualTo(150);
        cleanUp(useRedis);
    }

    @ParameterizedTest(name = "redis={0}")
    @ValueSource(booleans = {true, false})
    void crossingMidnightStartsNewDayButKeepsLastReading(boolean useRedis) {
        setUp(useRedis);
        LocalDateTime late = LocalDateTime.of(2026, 9, 23, 23, 50);
        read(IMEI, A, 9000, late);
        assertThat(read(IMEI, A, 9400, late.plusMinutes(5))).isEqualTo(400);
        assertThat(read(IMEI, A, 9460, late.plusMinutes(15))).as("0 点前最后一次读数之后的步数算进新的一天").isEqualTo(60);
        assertThat(read(IMEI, A, 9500, late.plusMinutes(20))).isEqualTo(100);
        cleanUp(useRedis);
    }

    @ParameterizedTest(name = "redis={0}")
    @ValueSource(booleans = {true, false})
    void rebindRestartsFromTheNewPersonsSteps(boolean useRedis) {
        setUp(useRedis);
        // B 今天先戴过另一块表，走了 300 步
        read(IMEI2, B, 700, T0);
        assertThat(read(IMEI2, B, 1000, T0.plusMinutes(10))).isEqualTo(300);
        // 表 1 本来是 A 的
        read(IMEI, A, 5000, T0);
        read(IMEI, A, 5100, T0.plusMinutes(10));
        // 表 1 改绑给 B：增量 0，接着 B 今天已有的 300 步
        assertThat(read(IMEI, B, 5200, T0.plusMinutes(20))).as("换绑：重新作起点").isEqualTo(300);
        assertThat(read(IMEI, B, 5250, T0.plusMinutes(25))).isEqualTo(350);
        cleanUp(useRedis);
    }

    @ParameterizedTest(name = "redis={0}")
    @ValueSource(booleans = {true, false})
    void lostStateResumesFromStepDaily(boolean useRedis) {
        setUp(useRedis);
        read(IMEI, A, 2000, T0);
        read(IMEI, A, 2500, T0.plusMinutes(10));
        // Redis 数据丢了 / 后端重启后进程内状态没了：从 STEP_DAILY 接着算，不从 0 开始、也不把 2500 当增量
        if (useRedis) {
            cleanKeys();
        }
        StepCounter fresh = new StepCounter(redis, bufferLike(useRedis), new FakeStepDaily(), propsLike());
        counter = fresh;
        assertThat(read(IMEI, A, 2600, T0.plusMinutes(20))).isEqualTo(600);
        cleanUp(useRedis);
    }

    // 重建一个同样模式的缓冲（lostState 用例里模拟重启）
    private WatchBuffer bufferLike(boolean useRedis) {
        WatchBuffer b = new WatchBuffer(redis, JsonMapper.builder().build(), propsLike(),
                new WatchMetrics(new SimpleMeterRegistry()));
        if (!useRedis) {
            b.push(WatchDatum.heartbeat(1, IMEI, A, 1, 80, null, null));
        }
        return b;
    }

    private WatchProperties propsLike() {
        return new WatchProperties(false, 0, 10, 20, 12, 60, Duration.ofSeconds(30), Duration.ofMinutes(10), 8192,
                60, 1, 10, new WatchProperties.Buffer(false, PREFIX, 5000, 1000, 100, 3, Duration.ofSeconds(1)));
    }

    private void cleanUp(boolean useRedis) {
        if (useRedis) {
            cleanKeys();
        }
    }

    /** 模拟 STEP_DAILY：取这个人不晚于某天的最近一天。 */
    private class FakeStepDaily implements WatchMapper {
        @Override
        public List<DeviceRow> selectEnabledDevices() {
            return List.of();
        }

        @Override
        public StepSeed selectStepSeed(String cardCode, LocalDate statDate) {
            return stepDaily.values().stream()
                    .filter(s -> stepDaily.get(cardCode + s.statDate()) == s && !s.statDate().isAfter(statDate))
                    .max((x, y) -> x.statDate().compareTo(y.statDate()))
                    .orElse(null);
        }
    }
}
