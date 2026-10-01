package com.xzkj.hv2.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.xzkj.hv2.watch.WatchDbTestBase;

/** 日汇总（docs/04 第五节 HEALTH_DAILY_SUMMARY）：统计值、按岗位类别算正常样本数、血压拆两项、重复执行不变。 */
class HealthDailySummaryJobTest extends WatchDbTestBase {

    private static final String MINER = "62082300920390001";
    private static final String OTHER = "62082300920390002";
    private static final LocalDate DAY = NOW.toLocalDate();
    private static final AtomicLong IDS = new AtomicLong(1);

    @Autowired
    private HealthSummaryMapper mapper;

    private HealthDailySummaryJob job;

    @BeforeEach
    void setUp() {
        job = new HealthDailySummaryJob(mapper, clock, meters);
        jobGroup("MINING", "采煤机司机", 50, 130);
        person(MINER, "采煤机司机");
        person(OTHER, null);
    }

    private void record(String card, String metric, String v1, String v2, LocalDateTime at) {
        jdbc.update("""
                INSERT INTO HEALTH_RECORD (MSG_ID, CARD_CODE, METRIC, VAL1, VAL2, COLLECTED_AT, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, ?, ?, '869900000000001')""",
                IDS.getAndIncrement(), card, metric, new BigDecimal(v1), v2 == null ? null : new BigDecimal(v2),
                Timestamp.valueOf(at));
    }

    private Map<String, Object> row(String card, String metric) {
        return jdbc.queryForMap("SELECT * FROM HEALTH_DAILY_SUMMARY WHERE STAT_DATE = ? AND CARD_CODE = ? AND METRIC = ?",
                java.sql.Date.valueOf(DAY), card, metric);
    }

    private static int i(Object v) {
        return ((Number) v).intValue();
    }

    @Test
    void summarisesADayWithEachPersonsGroupThresholds() {
        for (String card : new String[] {MINER, OTHER}) {
            record(card, "HEART_RATE", "80", null, NOW);
            record(card, "HEART_RATE", "125", null, NOW.plusMinutes(4));
            record(card, "HEART_RATE", "135", null, NOW.plusMinutes(8));
        }
        record(OTHER, "BLOOD_PRESSURE", "150", "95", NOW);
        record(OTHER, "BLOOD_PRESSURE", "120", "80", NOW.plusMinutes(4));
        // 别的日子的数据不算进来
        record(OTHER, "HEART_RATE", "200", null, NOW.minusDays(1));
        record(OTHER, "HEART_RATE", "40", null, DAY.plusDays(1).atStartOfDay());

        assertThat(job.run(DAY)).isEqualTo(4);

        Map<String, Object> miner = row(MINER, "HEART_RATE");
        assertThat(i(miner.get("SAMPLE_COUNT"))).isEqualTo(3);
        assertThat(i(miner.get("NORMAL_COUNT"))).as("采掘类上限 130：80、125 正常").isEqualTo(2);
        assertThat(miner.get("RULE_GROUP")).isEqualTo("MINING");
        assertThat((BigDecimal) miner.get("MAX_V")).isEqualByComparingTo("135");
        assertThat((BigDecimal) miner.get("MIN_V")).isEqualByComparingTo("80");
        assertThat((BigDecimal) miner.get("AVG_V")).isEqualByComparingTo("113.33");

        Map<String, Object> other = row(OTHER, "HEART_RATE");
        assertThat(i(other.get("NORMAL_COUNT"))).as("DEFAULT 上限 120：只有 80 正常").isEqualTo(1);
        assertThat(other.get("RULE_GROUP")).isEqualTo("DEFAULT");

        assertThat(i(row(OTHER, "BP_SYS").get("NORMAL_COUNT"))).as("收缩压 150 偏高").isEqualTo(1);
        assertThat((BigDecimal) row(OTHER, "BP_DIA").get("MAX_V")).isEqualByComparingTo("95");
    }

    @Test
    void rerunningGivesTheSameRowsAndPicksUpNewData() {
        record(MINER, "SPO2", "97", null, NOW);
        job.run(DAY);
        job.run(DAY);
        assertThat(count("SELECT COUNT(*) FROM HEALTH_DAILY_SUMMARY")).isEqualTo(1);
        record(MINER, "SPO2", "85", null, NOW.plusMinutes(5));
        job.run(DAY);
        Map<String, Object> r = row(MINER, "SPO2");
        assertThat(i(r.get("SAMPLE_COUNT"))).isEqualTo(2);
        assertThat(i(r.get("NORMAL_COUNT"))).as("血氧下限 90").isEqualTo(1);
    }
}
