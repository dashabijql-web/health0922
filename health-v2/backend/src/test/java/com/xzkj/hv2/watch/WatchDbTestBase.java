package com.xzkj.hv2.watch;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.micrometer.core.instrument.MeterRegistry;

import com.xzkj.hv2.alert.AlertRules;
import com.xzkj.hv2.common.config.TimeConfig;
import com.xzkj.hv2.support.MutableClock;
import com.xzkj.hv2.support.OracleTestDatabase;

/**
 * 手表链路的数据库测试共用：Oracle 测试用户、本机 Redis（只用 hv2:test:watch: 开头的 key）、可拨动的时钟。
 * 不监听 9001，不定时写库（测试里显式调用），见 src/test/resources/config/application.yml。
 * 每个测试前清空手表、体征、预警表，把 DEFAULT 阈值恢复成初始值。
 */
@SpringBootTest
@Import(WatchDbTestBase.TestClock.class)
public abstract class WatchDbTestBase {

    protected static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 10, 0, 0);
    protected static final String REDIS_PREFIX = "hv2:test:watch:";

    private static final List<String> CLEAR = List.of("ALERT_EVENT", "HEALTH_RECORD", "HEALTH_LATEST",
            "HEALTH_DAILY_SUMMARY", "STEP_DAILY", "DEVICE", "JOB_KIND_GROUP", "POS_PERSON");

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW, TimeConfig.ZONE);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", OracleTestDatabase::url);
        r.add("spring.datasource.username", OracleTestDatabase::user);
        r.add("spring.datasource.password", OracleTestDatabase::password);
        r.add("hv2.positioning.scan-enabled", () -> "false");
    }

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected StringRedisTemplate redis;
    @Autowired
    protected DeviceRegistry devices;
    @Autowired
    protected AlertRules rules;
    @Autowired
    protected MeterRegistry meters;

    @BeforeAll
    static void database() {
        assumeTrue(OracleTestDatabase.available(), "没有配置 ORACLE_TEST_USER，跳过");
        OracleTestDatabase.migrateOnce();
    }

    @BeforeEach
    void resetWatchTables() {
        CLEAR.forEach(t -> jdbc.update("DELETE FROM " + t));
        jdbc.update("DELETE FROM ALERT_RULE WHERE GROUP_CODE <> 'DEFAULT'");
        jdbc.update("DELETE FROM JOB_GROUP WHERE GROUP_CODE <> 'DEFAULT'");
        jdbc.update("UPDATE ALERT_RULE SET LOW_LIMIT = 50, HIGH_LIMIT = 120, ENABLED = 1 WHERE METRIC = 'HEART_RATE'");
        jdbc.update("UPDATE METRIC_COUNTER SET TOTAL_COUNT = 0");
        clock.set(NOW);
        if (redisAvailable()) {
            Set<String> keys = redis.keys(REDIS_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        devices.refresh();
        rules.refresh();
    }

    protected boolean redisAvailable() {
        try {
            return "PONG".equalsIgnoreCase(redis.execute(c -> c.ping(), true));
        } catch (RuntimeException e) {
            return false;
        }
    }

    protected void assumeRedis() {
        assumeTrue(redisAvailable(), "本机 Redis 不可用，跳过");
    }

    /** 加一个人（脱敏假数据）。 */
    protected void person(String card, String jobKind) {
        jdbc.update("""
                INSERT INTO POS_PERSON (CARD_CODE, PERSON_NAME, JOB_KIND, FROM_RYXX, SRC_DATA_TIME)
                VALUES (?, ?, ?, 1, ?)""", card, "测试" + card.substring(12), jobKind, Timestamp.valueOf(NOW));
    }

    /** 登记一块表；card 为 null 表示未绑定。 */
    protected void device(String imei, String card) {
        jdbc.update("INSERT INTO DEVICE (IMEI, CARD_CODE, BOUND_AT) VALUES (?, ?, ?)", imei, card,
                card == null ? null : Timestamp.valueOf(NOW));
        devices.refresh();
    }

    /** 新建一个岗位类别，并给它配心率阈值、把工种归进去。 */
    protected void jobGroup(String code, String jobKind, int hrLow, int hrHigh) {
        jdbc.update("INSERT INTO JOB_GROUP (GROUP_CODE, GROUP_NAME) VALUES (?, ?)", code, code);
        jdbc.update("""
                INSERT INTO ALERT_RULE (GROUP_CODE, METRIC, LOW_LIMIT, HIGH_LIMIT) VALUES (?, 'HEART_RATE', ?, ?)""",
                code, hrLow, hrHigh);
        jdbc.update("INSERT INTO JOB_KIND_GROUP (JOB_KIND, GROUP_CODE) VALUES (?, ?)", jobKind, code);
        rules.refresh();
    }

    protected int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    protected static long millis(LocalDateTime t) {
        return t.atZone(TimeConfig.ZONE).toInstant().toEpochMilli();
    }

    protected double dropped(String reason) {
        var c = meters.find("health.watch.dropped").tag("reason", reason).counter();
        return c == null ? 0 : c.count();
    }
}
