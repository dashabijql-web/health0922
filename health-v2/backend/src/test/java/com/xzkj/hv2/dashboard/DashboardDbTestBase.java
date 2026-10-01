package com.xzkj.hv2.dashboard;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;

import com.xzkj.hv2.watch.WatchDbTestBase;

/**
 * 大屏、导出的数据库测试共用：在手表测试的基础上再清空定位表和名单，提供造数据的小方法（脱敏假数据）。
 * 继承 {@link WatchDbTestBase}，和手表测试共用同一个 Spring 容器。
 */
abstract class DashboardDbTestBase extends WatchDbTestBase {

    static final LocalDate TODAY = NOW.toLocalDate();
    private static final AtomicLong IDS = new AtomicLong(1);

    private static final List<String> CLEAR = List.of("POS_INGEST_ERROR", "POS_INGEST_FILE", "POS_PERSON_STATE",
            "POS_AREA", "POS_PRESENCE_DAILY", "POS_HEADCOUNT_SERIES", "WATCH_LIST");

    @BeforeEach
    void clearDashboardTables() {
        CLEAR.forEach(t -> jdbc.update("DELETE FROM " + t));
    }

    static String card(int i) {
        return "620823009203" + String.format("%05d", 90000 + i);
    }

    static String imei(int i) {
        return "8699" + String.format("%011d", i);
    }

    /** 一个人，带部门；dept 为 null 表示厂家没给部门。 */
    void person(int i, String dept) {
        jdbc.update("""
                INSERT INTO POS_PERSON (CARD_CODE, PERSON_NAME, DEPT, FROM_RYXX, SRC_DATA_TIME)
                VALUES (?, ?, ?, 1, ?)""", card(i), "测试" + i, dept, Timestamp.valueOf(NOW));
    }

    void area(String code, String name) {
        jdbc.update("INSERT INTO POS_AREA (AREA_CODE, AREA_TYPE, AREA_NAME, SRC_DATA_TIME) VALUES (?, '其它区域', ?, ?)",
                code, name, Timestamp.valueOf(NOW));
    }

    /** 一份已处理的 RYSS（状态 DONE / PARTIAL 才算生效）。 */
    void ryss(LocalDateTime headerTime, String status) {
        jdbc.update("""
                INSERT INTO POS_INGEST_FILE (FILE_NAME, FILE_TYPE, MINE_CODE, HEADER_TIME, STATUS, RECEIVED_AT,
                                             PROCESSED_AT)
                VALUES (?, 'RYSS', '620823009203', ?, ?, ?, ?)""",
                "620823009203_RYSS_" + headerTime.toString().replaceAll("\\D", "") + ".txt",
                Timestamp.valueOf(headerTime), status, Timestamp.valueOf(headerTime), Timestamp.valueOf(headerTime));
    }

    /** 某人在某份 RYSS 里的状态：flag 1 已入井、2 已出井。 */
    void state(int i, int flag, String areaCode, LocalDateTime src) {
        jdbc.update("""
                INSERT INTO POS_PERSON_STATE (CARD_CODE, IN_OUT_FLAG, IN_TIME, AREA_CODE, WORK_STATUS, SRC_DATA_TIME)
                VALUES (?, ?, ?, ?, '正常', ?)""",
                card(i), flag, Timestamp.valueOf(src.minusHours(2)), areaCode, Timestamp.valueOf(src));
    }

    /** 一块表：card 为 null 表示未绑定；lastSeen 为 null 表示从没上行过。 */
    void watch(int i, String card, LocalDateTime lastSeen, int status) {
        jdbc.update("INSERT INTO DEVICE (IMEI, CARD_CODE, BOUND_AT, LAST_SEEN_AT, STATUS) VALUES (?, ?, ?, ?, ?)",
                imei(i), card, card == null ? null : Timestamp.valueOf(NOW.minusDays(1)),
                lastSeen == null ? null : Timestamp.valueOf(lastSeen), status);
    }

    void seen(int i, LocalDateTime lastSeen) {
        jdbc.update("UPDATE DEVICE SET LAST_SEEN_AT = ? WHERE IMEI = ?", Timestamp.valueOf(lastSeen), imei(i));
    }

    /** 直接写一条事件（同一天内 first ≤ last）。 */
    void event(String card, String imei, String src, String code, String category, LocalDateTime first,
               LocalDateTime last, int count) {
        jdbc.update("""
                INSERT INTO ALERT_EVENT (CARD_CODE, SRC, CODE, CATEGORY, SEVERITY, VAL_TEXT, OCCURRED_AT,
                                         LAST_OCCURRED_AT, OCCUR_COUNT, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, 2, ?, ?, ?, ?, ?)""",
                card, src, code, category, src.equals("THRESHOLD") ? "130" : "01", Timestamp.valueOf(first),
                Timestamp.valueOf(last), count, imei);
    }

    void summary(LocalDate day, int i, String metric, String max, String min, String avg, int samples) {
        jdbc.update("""
                INSERT INTO HEALTH_DAILY_SUMMARY (STAT_DATE, CARD_CODE, METRIC, MAX_V, MIN_V, AVG_V, SAMPLE_COUNT,
                                                  NORMAL_COUNT, RULE_GROUP, UPDATED_AT)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DEFAULT', ?)""",
                Date.valueOf(day), card(i), metric, new BigDecimal(max), new BigDecimal(min), new BigDecimal(avg),
                samples, samples, Timestamp.valueOf(NOW.minusMinutes(i)));
    }

    void steps(LocalDate day, int i, long steps) {
        jdbc.update("""
                INSERT INTO STEP_DAILY (STAT_DATE, CARD_CODE, STEPS, LAST_RAW, UPDATED_AT, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, ?, ?)""",
                Date.valueOf(day), card(i), steps, steps, Timestamp.valueOf(day.atTime(9, 0)), imei(i));
    }

    void record(int i, String metric, LocalDateTime at) {
        jdbc.update("""
                INSERT INTO HEALTH_RECORD (MSG_ID, CARD_CODE, METRIC, VAL1, VAL2, COLLECTED_AT, DEVICE_IMEI)
                VALUES (?, ?, ?, 80, NULL, ?, ?)""", IDS.getAndIncrement(), card(i), metric, Timestamp.valueOf(at),
                imei(i));
    }

    void watchList(int i, String type, LocalDate expire, LocalDateTime addedAt) {
        jdbc.update("""
                INSERT INTO WATCH_LIST (CARD_CODE, LIST_TYPE, EXPIRE_DATE, ADDED_BY, ADDED_AT)
                VALUES (?, ?, ?, 'admin', ?)""",
                card(i), type, expire == null ? null : Date.valueOf(expire), Timestamp.valueOf(addedAt));
    }

    void presence(LocalDate day, int i) {
        jdbc.update("""
                INSERT INTO POS_PRESENCE_DAILY (STAT_DATE, CARD_CODE, FIRST_IN_TIME, LAST_SEEN_TIME)
                VALUES (?, ?, ?, ?)""",
                Date.valueOf(day), card(i), Timestamp.valueOf(day.atTime(8, 0)), Timestamp.valueOf(day.atTime(15, 0)));
    }

    void headcountPoint(LocalDateTime t, int inWell) {
        jdbc.update("""
                INSERT INTO POS_HEADCOUNT_SERIES (SNAPSHOT_TIME, IN_WELL_COUNT, OUT_COUNT, TOTAL_COUNT)
                VALUES (?, ?, 0, ?)""", Timestamp.valueOf(t), inWell, inWell);
    }

    Integer sqlInt(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }
}
