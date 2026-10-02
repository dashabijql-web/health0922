package com.xzkj.hv2.report;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.SqlParameterValue;

import com.xzkj.hv2.watch.WatchDbTestBase;

/**
 * 月报的数据库测试共用：在手表测试的基础上再清空每日出入井，把 DEFAULT 类别的月报参数恢复成初始值
 * （90 / 70、5 天、3 条），清掉月报缓存。造数据的小方法都直接写汇总表（脱敏假数据）。
 * "现在"是 WatchDbTestBase 的 2026-09-23 10:00，所以 8 月是已结束的月份，9 月是当前月。
 */
abstract class ReportDbTestBase extends WatchDbTestBase {

    static final String MINE = "620823009203";

    @Autowired
    MonthlyReportService service;

    @BeforeEach
    void resetReportTables() {
        jdbc.update("DELETE FROM POS_PRESENCE_DAILY");
        jdbc.update("UPDATE ALERT_RULE SET STABLE_PCT = 90, UNSTABLE_PCT = 70 WHERE GROUP_CODE = 'DEFAULT'");
        jdbc.update("UPDATE JOB_GROUP SET MIN_EVAL_DAYS = 5, RISK_EVENT_COUNT = 3 WHERE GROUP_CODE = 'DEFAULT'");
        service.clearCache();
    }

    static String card(int i) {
        return MINE + String.format("%05d", 90000 + i);
    }

    static String imei(int i) {
        return "8699" + String.format("%011d", i);
    }

    static LocalDate aug(int day) {
        return LocalDate.of(2026, 8, day);
    }

    void person(int i, String name, String jobKind, String dept) {
        jdbc.update("""
                INSERT INTO POS_PERSON (CARD_CODE, PERSON_NAME, JOB_KIND, DEPT, FROM_RYXX, SRC_DATA_TIME)
                VALUES (?, ?, ?, ?, 1, ?)""", card(i), name, jobKind, dept, Timestamp.valueOf(NOW));
    }

    /** 第 i 个人 day 这天入过井。 */
    void presence(int i, LocalDate day) {
        jdbc.update("""
                INSERT INTO POS_PRESENCE_DAILY (STAT_DATE, CARD_CODE, FIRST_IN_TIME, LAST_SEEN_TIME)
                VALUES (?, ?, ?, ?)""", Date.valueOf(day), card(i), Timestamp.valueOf(day.atTime(8, 0)),
                Timestamp.valueOf(day.atTime(15, 0)));
    }

    /** 一行日汇总：samples 个样本里 normals 个在正常范围内。 */
    void summary(int i, LocalDate day, String metric, String max, String min, String avg, int samples, int normals) {
        jdbc.update("""
                INSERT INTO HEALTH_DAILY_SUMMARY (STAT_DATE, CARD_CODE, METRIC, MAX_V, MIN_V, AVG_V, SAMPLE_COUNT,
                                                  NORMAL_COUNT, RULE_GROUP)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DEFAULT')""",
                Date.valueOf(day), card(i), metric, new BigDecimal(max), new BigDecimal(min), new BigDecimal(avg),
                samples, normals);
    }

    /** 心率的日汇总：只关心正常率时用。 */
    void hr(int i, LocalDate day, int samples, int normals) {
        summary(i, day, "HEART_RATE", "130", "55", "80", samples, normals);
    }

    void steps(int i, LocalDate day, long steps) {
        jdbc.update("""
                INSERT INTO STEP_DAILY (STAT_DATE, CARD_CODE, STEPS, LAST_RAW, UPDATED_AT, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, ?, ?)""",
                Date.valueOf(day), card(i), steps, steps, Timestamp.valueOf(day.atTime(20, 0)), imei(i));
    }

    /**
     * 一条事件。
     *
     * @param i 第几个人；null 表示没绑定的手表（只有设备号 imei(99)）
     */
    void event(Integer i, String code, String category, LocalDateTime at) {
        boolean device = category.equals("SOS") || category.equals("FALL") || category.equals("OTHER");
        // 事件不跨天（CK_ALERT_EVENT_SAME_DAY）
        LocalDateTime last = at.plusMinutes(3).toLocalDate().equals(at.toLocalDate()) ? at.plusMinutes(3) : at;
        jdbc.update("""
                INSERT INTO ALERT_EVENT (CARD_CODE, SRC, CODE, CATEGORY, SEVERITY, VAL_TEXT, RULE_GROUP, OCCURRED_AT,
                                         LAST_OCCURRED_AT, OCCUR_COUNT, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, 2, '1', ?, ?, ?, 1, ?)""",
                i == null ? null : card(i), device ? "DEVICE" : "THRESHOLD", code, category,
                device ? null : "DEFAULT", Timestamp.valueOf(at), Timestamp.valueOf(last),
                i == null ? imei(99) : imei(i));
    }

    /**
     * 新建一个岗位类别，三项指标都配一行阈值（正常范围和 DEFAULT 一样），月报参数按给的值（null 表示没配，用 DEFAULT 的），
     * 再把工种归进去。
     */
    void group(String code, Integer minDays, Integer riskEvents, Integer stablePct, Integer unstablePct,
               String... jobKinds) {
        jdbc.update("INSERT INTO JOB_GROUP (GROUP_CODE, GROUP_NAME, SORT_NO, MIN_EVAL_DAYS, RISK_EVENT_COUNT) "
                + "VALUES (?, ?, 1, ?, ?)", code, code + "类", minDays, riskEvents);
        jdbc.update("""
                INSERT INTO ALERT_RULE (GROUP_CODE, METRIC, LOW_LIMIT, HIGH_LIMIT, STABLE_PCT, UNSTABLE_PCT)
                SELECT ?, METRIC, LOW_LIMIT, HIGH_LIMIT, ?, ? FROM ALERT_RULE
                 WHERE GROUP_CODE = 'DEFAULT' AND METRIC IN ('HEART_RATE', 'TEMPERATURE', 'SPO2')""",
                // INSERT … SELECT 里绑 null 时驱动推不出类型，要写明
                code, new SqlParameterValue(Types.NUMERIC, stablePct), new SqlParameterValue(Types.NUMERIC, unstablePct));
        for (String k : jobKinds) {
            jdbc.update("INSERT INTO JOB_KIND_GROUP (JOB_KIND, GROUP_CODE) VALUES (?, ?)", k, code);
        }
    }

    MonthlyReportViews.MonthlyReport august() {
        return service.monthly(java.time.YearMonth.of(2026, 8));
    }
}
