package com.xzkj.hv2.archive;

import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.xzkj.hv2.auth.CurrentUser;
import com.xzkj.hv2.auth.SessionGateway;
import com.xzkj.hv2.watch.WatchDbTestBase;

/**
 * 档案的数据库测试共用：在手表测试的基础上再清空定位表和名单，提供造数据的小方法（脱敏假数据）。
 * 当前登录用户换成假的（{@link #session}）。
 * <p>
 * 操作日志删不掉（触发器），查日志时只看这个测试开始之后写的（{@link #logs}）。
 */
abstract class ArchiveDbTestBase extends WatchDbTestBase {

    static final LocalDate TODAY = NOW.toLocalDate();
    static final String MINE = "620823009203";
    static final String AREA = MINE + "0001";
    static final String STATION = AREA + "000012";

    private static final List<String> CLEAR = List.of("POS_INGEST_ERROR", "POS_INGEST_FILE", "POS_PERSON_STATE",
            "POS_AREA", "POS_STATION", "POS_STATION_MARK", "POS_PRESENCE_DAILY", "POS_HEADCOUNT_SERIES",
            "WATCH_LIST");

    @MockitoBean
    SessionGateway session;

    /** 这个测试开始前操作日志的最大 ID */
    private long logStart;

    @BeforeEach
    void clearArchiveTables() {
        CLEAR.forEach(t -> jdbc.update("DELETE FROM " + t));
        loginAs("admin");
        Long max = jdbc.queryForObject("SELECT NVL(MAX(ID), 0) FROM SYS_OPERATION_LOG", Long.class);
        logStart = max == null ? 0 : max;
    }

    void loginAs(String username) {
        when(session.currentUser()).thenReturn(new CurrentUser(1, username));
    }

    static String card(int i) {
        return MINE + String.format("%05d", 90000 + i);
    }

    static String imei(int i) {
        return "8699" + String.format("%011d", i);
    }

    void person(int i, String name, String jobKind, String dept, Integer age) {
        jdbc.update("""
                INSERT INTO POS_PERSON (CARD_CODE, PERSON_NAME, JOB_KIND, DEPT, AGE, FROM_RYXX, SRC_DATA_TIME)
                VALUES (?, ?, ?, ?, ?, 1, ?)""", card(i), name, jobKind, dept, age, Timestamp.valueOf(NOW));
    }

    /** 给第 i 个人绑一块启用（status = 1）或停用（0）的手表。 */
    void bind(int i, int status, LocalDateTime lastSeen) {
        jdbc.update("""
                INSERT INTO DEVICE (IMEI, CARD_CODE, BOUND_AT, LAST_SEEN_AT, BATTERY_PCT, BATTERY_TIME, STATUS)
                VALUES (?, ?, ?, ?, 76, ?, ?)""",
                imei(i), card(i), Timestamp.valueOf(NOW.minusDays(1)),
                lastSeen == null ? null : Timestamp.valueOf(lastSeen),
                lastSeen == null ? null : Timestamp.valueOf(lastSeen), status);
    }

    void record(int i, String metric, String val1, String val2, LocalDateTime at, long msgId) {
        jdbc.update("""
                INSERT INTO HEALTH_RECORD (MSG_ID, CARD_CODE, METRIC, VAL1, VAL2, COLLECTED_AT, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                msgId, card(i), metric, new BigDecimal(val1), val2 == null ? null : new BigDecimal(val2),
                Timestamp.valueOf(at), imei(i));
    }

    void latest(int i, String metric, String val1, String val2, LocalDateTime at) {
        jdbc.update("""
                INSERT INTO HEALTH_LATEST (CARD_CODE, METRIC, VAL1, VAL2, COLLECTED_AT, MSG_ID, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, ?, 1, ?)""",
                card(i), metric, new BigDecimal(val1), val2 == null ? null : new BigDecimal(val2),
                Timestamp.valueOf(at), imei(i));
    }

    void steps(int i, LocalDate day, long steps) {
        jdbc.update("""
                INSERT INTO STEP_DAILY (STAT_DATE, CARD_CODE, STEPS, LAST_RAW, UPDATED_AT, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, ?, ?)""",
                Date.valueOf(day), card(i), steps, steps, Timestamp.valueOf(day.atTime(9, 30)), imei(i));
    }

    void event(int i, String src, String code, String category, String val, LocalDateTime first, int count) {
        jdbc.update("""
                INSERT INTO ALERT_EVENT (CARD_CODE, SRC, CODE, CATEGORY, SEVERITY, VAL_TEXT, OCCURRED_AT,
                                         LAST_OCCURRED_AT, OCCUR_COUNT, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, 2, ?, ?, ?, ?, ?)""",
                card(i), src, code, category, val, Timestamp.valueOf(first), Timestamp.valueOf(first.plusMinutes(5)),
                count, imei(i));
    }

    /** 第 i 个人在最新一份 RYSS 里，已入井，在 STATION 基站。 */
    void inWell(int i, LocalDateTime src) {
        jdbc.update("""
                INSERT INTO POS_INGEST_FILE (FILE_NAME, FILE_TYPE, MINE_CODE, HEADER_TIME, STATUS, RECEIVED_AT)
                VALUES (?, 'RYSS', ?, ?, 'DONE', ?)""",
                MINE + "_RYSS_" + i + ".txt", MINE, Timestamp.valueOf(src), Timestamp.valueOf(src));
        jdbc.update("""
                INSERT INTO POS_PERSON_STATE (CARD_CODE, IN_OUT_FLAG, IN_TIME, AREA_CODE, STATION_CODE,
                                              STATION_ENTER_TIME, WORK_STATUS, SRC_DATA_TIME)
                VALUES (?, 1, ?, ?, ?, ?, '正常', ?)""",
                card(i), Timestamp.valueOf(src.minusHours(2)), AREA, STATION, Timestamp.valueOf(src.minusMinutes(20)),
                Timestamp.valueOf(src));
    }

    /** 这个测试里写的操作日志，先写的在前，每行"动作|操作人|对象|改动前|改动后"。 */
    List<String> logs() {
        return jdbc.queryForList("""
                SELECT ACTION || '|' || USERNAME || '|' || TARGET_TYPE || ':' || TARGET_ID || '|'
                       || DBMS_LOB.SUBSTR(BEFORE_JSON, 400) || '|' || DBMS_LOB.SUBSTR(AFTER_JSON, 400)
                  FROM SYS_OPERATION_LOG
                 WHERE ID > ?
                 ORDER BY ID""", String.class, logStart);
    }
}
