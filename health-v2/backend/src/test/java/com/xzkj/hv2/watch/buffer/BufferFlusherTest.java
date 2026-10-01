package com.xzkj.hv2.watch.buffer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;

import com.xzkj.hv2.watch.WatchDbTestBase;
import com.xzkj.hv2.watch.WatchMetrics;
import com.xzkj.hv2.watch.WatchProperties;

/** 缓冲与批量写库（docs/03 第五节）：重复处理不重复入库、坏数据只让它自己进死信、Redis 不可用时降级。 */
class BufferFlusherTest extends WatchDbTestBase {

    private static final String IMEI = "869900000000001";
    private static final String A = "62082300920390001";

    @Autowired
    private WatchBuffer buffer;
    @Autowired
    private BufferFlusher flusher;
    @Autowired
    private WatchBatchWriter writer;
    @Autowired
    private MsgIds msgIds;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private WatchProperties props;

    @BeforeEach
    void setUp() {
        assumeRedis();
        person(A, null);
        device(IMEI, A);
    }

    private WatchDatum hr(int v, int secondOffset) {
        long at = millis(NOW.plusSeconds(secondOffset));
        return WatchDatum.vital(msgIds.next(at), IMEI, A, at, "HEART_RATE", BigDecimal.valueOf(v), null);
    }

    private List<WatchDatum> batch(int n) {
        List<WatchDatum> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(hr(60 + i, i));
        }
        return out;
    }

    private long dead() {
        Long n = redis.opsForList().size(REDIS_PREFIX + "dead");
        return n == null ? 0 : n;
    }

    @Test
    void batchCommittedButNotRemovedIsReplayedWithoutDuplicates() {
        List<WatchDatum> items = batch(50);
        // 模拟：这一批已经提交，但删除 processing 之前进程被杀
        writer.write(items);
        items.forEach(d -> redis.opsForList().rightPush(REDIS_PREFIX + "processing", json.writeValueAsString(d)));

        assertThat(flusher.flushRound()).isTrue();

        assertThat(count("SELECT COUNT(*) FROM HEALTH_RECORD")).isEqualTo(50);
        assertThat(count("SELECT COUNT(*) FROM (SELECT MSG_ID FROM HEALTH_RECORD GROUP BY MSG_ID HAVING COUNT(*) > 1)"))
                .isZero();
        assertThat(count("SELECT TOTAL_COUNT FROM METRIC_COUNTER WHERE METRIC = 'HEART_RATE'"))
                .as("计数只加真正新插入的").isEqualTo(50);
        assertThat(redis.opsForList().size(REDIS_PREFIX + "processing")).isZero();
    }

    @Test
    void batchLeftInProcessingBeforeCommitIsWrittenFirst() {
        batch(30).forEach(d -> redis.opsForList().rightPush(REDIS_PREFIX + "processing", json.writeValueAsString(d)));
        batch(20).forEach(buffer::push);
        assertThat(flusher.flushRound()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM HEALTH_RECORD")).isEqualTo(50);
        assertThat(count("SELECT TOTAL_COUNT FROM METRIC_COUNTER WHERE METRIC = 'HEART_RATE'")).isEqualTo(50);
    }

    @Test
    void oneBadItemGoesToDeadLetterAndTheRestAreSaved() {
        List<WatchDatum> items = batch(9);
        long at = millis(NOW.plusSeconds(100));
        // NUMBER(6,1) 放不下：ORA-01438，写不进库
        WatchDatum bad = WatchDatum.vital(msgIds.next(at), IMEI, A, at, "HEART_RATE", new BigDecimal("123456789"), null);
        items.add(4, bad);
        items.forEach(buffer::push);
        double splits = meters.counter("health.buffer.split").count();

        assertThat(flusher.flushRound()).isTrue();

        assertThat(count("SELECT COUNT(*) FROM HEALTH_RECORD")).isEqualTo(9);
        assertThat(dead()).isEqualTo(1);
        assertThat(redis.opsForList().index(REDIS_PREFIX + "dead", 0)).contains("\"msgId\":" + bad.msgId());
        assertThat(meters.counter("health.buffer.split").count() - splits).isGreaterThan(0);
        assertThat(count("SELECT TOTAL_COUNT FROM METRIC_COUNTER WHERE METRIC = 'HEART_RATE'")).isEqualTo(9);
    }

    @Test
    void unparsableItemGoesStraightToDeadLetter() {
        redis.opsForList().rightPush(REDIS_PREFIX + "buf", "{not json");
        buffer.push(hr(70, 0));
        assertThat(flusher.flushRound()).isTrue();
        assertThat(dead()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM HEALTH_RECORD")).isEqualTo(1);
    }

    @Test
    void moreThanOneBatchIsDrainedInOneRound() {
        batch(props.buffer().batchSize() * 2 + 7).forEach(buffer::push);
        assertThat(flusher.flushRound()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM HEALTH_RECORD")).isEqualTo(props.buffer().batchSize() * 2 + 7);
        assertThat(flusher.backlog()).isZero();
    }

    @Test
    void whenRedisIsDownDataGoesThroughBoundedMemoryQueue() {
        LettuceConnectionFactory deadRedis = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).build());
        deadRedis.afterPropertiesSet();
        deadRedis.start();
        try {
            WatchProperties small = new WatchProperties(false, 0, 10, 20, 12, 60, Duration.ofSeconds(30),
                    Duration.ofMinutes(10), 8192, 60, 1, 10,
                    new WatchProperties.Buffer(false, REDIS_PREFIX, 5000, 1000, 5, 3, Duration.ofSeconds(1)));
            SimpleMeterRegistry reg = new SimpleMeterRegistry();
            WatchMetrics m = new WatchMetrics(reg);
            WatchBuffer down = new WatchBuffer(new StringRedisTemplate(deadRedis), json, small, m);
            BufferFlusher f = new BufferFlusher(down, writer, devices, small, m);

            batch(7).forEach(down::push);
            assertThat(down.redisMode()).isFalse();
            assertThat(reg.counter("health.buffer.memory.dropped").count()).as("上限 5 条，丢最旧的 2 条").isEqualTo(2);

            assertThat(f.flushRound()).isTrue();
            assertThat(jdbc.queryForList("SELECT VAL1 FROM HEALTH_RECORD ORDER BY VAL1", Integer.class))
                    .containsExactly(62, 63, 64, 65, 66);
            assertThat(down.redisMode()).as("Redis 还没恢复，继续用内存队列").isFalse();
        } finally {
            deadRedis.destroy();
        }
    }
}
