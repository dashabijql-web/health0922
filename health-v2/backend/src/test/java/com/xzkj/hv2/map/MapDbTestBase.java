package com.xzkj.hv2.map;

import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.xzkj.hv2.auth.CurrentUser;
import com.xzkj.hv2.auth.SessionGateway;
import com.xzkj.hv2.watch.WatchDbTestBase;

/**
 * 地图的数据库测试共用：在手表测试的基础上再清空定位表、摆放、名单，提供造数据的小方法（脱敏假数据）。
 * 当前登录用户换成假的（{@link #session}）。
 * <p>
 * 操作日志删不掉（触发器），所以每个测试用自己的基站编码（{@link #newStation}），查日志时按基站编码过滤。
 */
abstract class MapDbTestBase extends WatchDbTestBase {

    static final LocalDate TODAY = NOW.toLocalDate();
    static final String MINE = "620823009203";
    static final String AREA_A = MINE + "0001";
    static final String AREA_B = MINE + "0002";
    /** 地图范围内的一个点（docs/06 第三节的范围） */
    static final BigDecimal X0 = new BigDecimal("39482000.5");
    static final BigDecimal Y0 = new BigDecimal("3852000.5");

    private static final AtomicInteger STATIONS = new AtomicInteger(1);
    private static final List<String> CLEAR = List.of("POS_INGEST_ERROR", "POS_INGEST_FILE", "POS_PERSON_STATE",
            "POS_AREA", "POS_STATION", "POS_STATION_MARK", "POS_PRESENCE_DAILY", "POS_HEADCOUNT_SERIES",
            "WATCH_LIST");

    @MockitoBean
    SessionGateway session;

    @BeforeEach
    void clearMapTables() {
        CLEAR.forEach(t -> jdbc.update("DELETE FROM " + t));
        loginAs("admin");
        area(AREA_A, "下口区域");
        area(AREA_B, "-750大巷");
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

    /** 一个新的基站编码：区域编码 + 6 位，整个测试运行期间不重复。 */
    static String newStationCode(String areaCode) {
        return areaCode + String.format("%06d", STATIONS.getAndIncrement());
    }

    /** 厂家的一个基站；name 为 null 表示厂家没给名称。 */
    String newStation(String areaCode, String name, Integer runStatus) {
        String code = newStationCode(areaCode);
        jdbc.update("INSERT INTO POS_STATION (STATION_CODE, STATION_NAME, RUN_STATUS, NAME_SRC_TIME) VALUES (?, ?, ?, ?)",
                code, name, runStatus, Timestamp.valueOf(NOW));
        return code;
    }

    void area(String code, String name) {
        jdbc.update("INSERT INTO POS_AREA (AREA_CODE, AREA_TYPE, AREA_NAME, SRC_DATA_TIME) VALUES (?, '其它区域', ?, ?)",
                code, name, Timestamp.valueOf(NOW));
    }

    void person(int i, String name, String dept) {
        jdbc.update("""
                INSERT INTO POS_PERSON (CARD_CODE, PERSON_NAME, DEPT, FROM_RYXX, SRC_DATA_TIME)
                VALUES (?, ?, ?, 1, ?)""", card(i), name, dept, Timestamp.valueOf(NOW));
    }

    /** 一份生效的 RYSS。 */
    void ryss(LocalDateTime headerTime) {
        jdbc.update("""
                INSERT INTO POS_INGEST_FILE (FILE_NAME, FILE_TYPE, MINE_CODE, HEADER_TIME, STATUS, RECEIVED_AT)
                VALUES (?, 'RYSS', ?, ?, 'DONE', ?)""",
                MINE + "_RYSS_" + headerTime.toString().replaceAll("\\D", "") + ".txt", MINE,
                Timestamp.valueOf(headerTime), Timestamp.valueOf(headerTime));
    }

    /** 某人在某份 RYSS 里的位置：flag 1 已入井、2 已出井。 */
    void state(int i, int flag, String stationCode, LocalDateTime src) {
        jdbc.update("""
                INSERT INTO POS_PERSON_STATE (CARD_CODE, IN_OUT_FLAG, IN_TIME, AREA_CODE, STATION_CODE,
                                              STATION_ENTER_TIME, WORK_STATUS, SRC_DATA_TIME)
                VALUES (?, ?, ?, ?, ?, ?, '正常', ?)""",
                card(i), flag, Timestamp.valueOf(src.minusHours(2)), stationCode.substring(0, 16), stationCode,
                Timestamp.valueOf(src.minusMinutes(20)), Timestamp.valueOf(src));
    }

    /** 直接写一条摆放（不经过服务，不记日志）。 */
    void mark(String stationCode, BigDecimal x, BigDecimal y, String displayName) {
        jdbc.update("""
                INSERT INTO POS_STATION_MARK (STATION_CODE, DISPLAY_NAME, X, Y, PLACED_BY, VERSION)
                VALUES (?, ?, ?, ?, 'admin', 1)""", stationCode, displayName, x, y);
    }

    void watch(int i, LocalDateTime lastSeen, Integer battery, LocalDateTime batteryTime) {
        jdbc.update("""
                INSERT INTO DEVICE (IMEI, CARD_CODE, BOUND_AT, LAST_SEEN_AT, BATTERY_PCT, BATTERY_TIME, STATUS)
                VALUES (?, ?, ?, ?, ?, ?, 1)""",
                imei(i), card(i), Timestamp.valueOf(NOW.minusDays(1)),
                lastSeen == null ? null : Timestamp.valueOf(lastSeen), battery,
                batteryTime == null ? null : Timestamp.valueOf(batteryTime));
    }

    void latest(int i, String metric, String val1, String val2, LocalDateTime at) {
        jdbc.update("""
                INSERT INTO HEALTH_LATEST (CARD_CODE, METRIC, VAL1, VAL2, COLLECTED_AT, MSG_ID, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, ?, 1, ?)""",
                card(i), metric, new BigDecimal(val1), val2 == null ? null : new BigDecimal(val2),
                Timestamp.valueOf(at), imei(i));
    }

    void event(int i, String src, String code, String category, String val, LocalDateTime first, LocalDateTime last) {
        jdbc.update("""
                INSERT INTO ALERT_EVENT (CARD_CODE, SRC, CODE, CATEGORY, SEVERITY, VAL_TEXT, OCCURRED_AT,
                                         LAST_OCCURRED_AT, OCCUR_COUNT, DEVICE_IMEI)
                VALUES (?, ?, ?, ?, 2, ?, ?, ?, 1, ?)""",
                card(i), src, code, category, val, Timestamp.valueOf(first), Timestamp.valueOf(last), imei(i));
    }

    void watchList(int i, String type, LocalDate expire) {
        jdbc.update("""
                INSERT INTO WATCH_LIST (CARD_CODE, LIST_TYPE, EXPIRE_DATE, ADDED_BY) VALUES (?, ?, ?, 'admin')""",
                card(i), type, expire == null ? null : Date.valueOf(expire));
    }

    /** 某基站的操作日志：最新的在前，每行"动作|操作人|改动前|改动后"。 */
    List<String> logs(String stationCode) {
        return jdbc.queryForList("""
                SELECT ACTION || '|' || USERNAME || '|' || DBMS_LOB.SUBSTR(BEFORE_JSON, 400)
                       || '|' || DBMS_LOB.SUBSTR(AFTER_JSON, 400)
                  FROM SYS_OPERATION_LOG
                 WHERE TARGET_TYPE = 'STATION' AND TARGET_ID = ?
                 ORDER BY ID DESC""", String.class, stationCode);
    }
}
