package com.xzkj.hv2.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.map.MapViews.MapPerson;
import com.xzkj.hv2.map.MapViews.PersonCard;
import com.xzkj.hv2.map.MapViews.Station;
import com.xzkj.hv2.map.MapViews.StationList;
import com.xzkj.hv2.map.MapViews.StationLog;

/** 地图模式的查询和基站摆放（docs/05 第五节、docs/06 第五～七节），连 Oracle 测试用户。 */
class MapServiceTest extends MapDbTestBase {

    @Autowired
    MapService maps;
    @Autowired
    StationMarkService marks;

    // ================= 人员 =================

    @Test
    void personsAtPlacedStationHaveCoordinatesOthersOnlyAreaName() {
        String placed = newStation(AREA_A, "新副井下口1", 0);
        String unplaced = newStation(AREA_B, null, 0);
        mark(placed, X0, Y0, null);
        ryss(NOW.minusMinutes(3));
        person(1, "张一", "综采一队");
        person(2, "李二", null);
        person(3, "王三", "机电队");
        state(1, 1, placed, NOW.minusMinutes(3));
        state(2, 1, unplaced, NOW.minusMinutes(3));
        state(3, 2, placed, NOW.minusMinutes(3)); // 已出井：不画

        List<MapPerson> list = maps.persons(null, null);

        assertThat(list).extracting(MapPerson::cardCode).containsExactly(card(1), card(2));
        MapPerson a = list.get(0);
        assertThat(a.placed()).isTrue();
        assertThat(a.x()).isEqualByComparingTo(X0);
        assertThat(a.y()).isEqualByComparingTo(Y0);
        assertThat(a.stationName()).isEqualTo("新副井下口1");
        assertThat(a.areaName()).isEqualTo("下口区域");
        assertThat(a.posTime()).isEqualTo(NOW.minusMinutes(3));
        MapPerson b = list.get(1);
        assertThat(b.placed()).isFalse();
        assertThat(b.x()).isNull();
        // 厂家没给名称：区域名称 + 编码后 6 位
        assertThat(b.stationName()).isEqualTo("-750大巷 " + unplaced.substring(16));
        assertThat(b.areaName()).isEqualTo("-750大巷");
    }

    @Test
    void personsUseOnlyTheLatestEffectiveRyss() {
        String st = newStation(AREA_A, "甲", 0);
        ryss(NOW.minusMinutes(8));
        ryss(NOW.minusMinutes(3));
        person(1, "张一", null);
        person(2, "李二", null);
        state(1, 1, st, NOW.minusMinutes(3));
        state(2, 1, st, NOW.minusMinutes(8)); // 不在最新一份 RYSS 里

        assertThat(maps.persons(null, null)).extracting(MapPerson::cardCode).containsExactly(card(1));
    }

    @Test
    void filterByWatchListAndKeyword() {
        String st = newStation(AREA_A, "甲", 0);
        ryss(NOW.minusMinutes(3));
        for (int i = 1; i <= 4; i++) {
            person(i, "测试" + i, null);
            state(i, 1, st, NOW.minusMinutes(3));
        }
        watchList(1, "KEY", null);
        watchList(2, "TODAY", TODAY);
        watchList(3, "TODAY", TODAY.minusDays(1)); // 昨天的"今日关注"已失效

        assertThat(maps.persons("KEY", null)).extracting(MapPerson::cardCode).containsExactly(card(1));
        assertThat(maps.persons("TODAY", null)).extracting(MapPerson::cardCode).containsExactly(card(2));
        assertThat(maps.persons(null, "测试4")).extracting(MapPerson::cardCode).containsExactly(card(4));
        // 卡号（后 5 位）也能查；% 不是通配符
        assertThat(maps.persons(null, card(3).substring(12))).extracting(MapPerson::cardCode)
                .containsExactly(card(3));
        assertThat(maps.persons(null, "%")).isEmpty();
    }

    @Test
    void watchStateAndActiveAlert() {
        String st = newStation(AREA_A, "甲", 0);
        ryss(NOW.minusMinutes(3));
        for (int i = 1; i <= 3; i++) {
            person(i, "测试" + i, null);
            state(i, 1, st, NOW.minusMinutes(3));
        }
        watch(1, NOW.minusMinutes(5), 80, NOW.minusMinutes(5));
        watch(2, NOW.minusMinutes(60), null, null);
        // 1 号 10 分钟前心率偏高：仍在发生；2 号 40 分钟前的：已过去；设备事件不算
        event(1, "THRESHOLD", "HR_HIGH", "HEART_RATE", "132", NOW.minusMinutes(20), NOW.minusMinutes(10));
        event(2, "THRESHOLD", "HR_HIGH", "HEART_RATE", "132", NOW.minusMinutes(50), NOW.minusMinutes(40));
        event(3, "DEVICE", "LOW_BATTERY", "OTHER", "03", NOW.minusMinutes(5), NOW.minusMinutes(5));

        List<MapPerson> list = maps.persons(null, null);

        assertThat(list).extracting(MapPerson::watchState).containsExactly("ONLINE", "OFFLINE", "UNBOUND");
        assertThat(list).extracting(MapPerson::alerting).containsExactly(true, false, false);
    }

    // ================= 体征卡 =================

    @Test
    void cardHasVitalsWithTimesBatteryWatchAndLastThreeAlerts() {
        String st = newStation(AREA_A, "新副井下口1", 1); // 通讯中断
        mark(st, X0, Y0, "副井口");
        ryss(NOW.minusMinutes(3));
        person(1, "张一", "综采一队");
        state(1, 1, st, NOW.minusMinutes(3));
        watch(1, NOW.minusMinutes(2), 76, NOW.minusMinutes(2));
        latest(1, "HEART_RATE", "72", null, NOW.minusMinutes(5));
        latest(1, "TEMPERATURE", "36.6", null, NOW.minusMinutes(30)); // 超过 12 分钟：数据较旧
        latest(1, "BLOOD_PRESSURE", "121", "79", NOW.minusMinutes(1));
        event(1, "THRESHOLD", "HR_HIGH", "HEART_RATE", "132", NOW.minusHours(5), NOW.minusHours(5));
        event(1, "DEVICE", "SOS", "SOS", "01", NOW.minusHours(4), NOW.minusHours(4));
        event(1, "THRESHOLD", "TEMP_HIGH", "TEMPERATURE", "37.8", NOW.minusHours(3), NOW.minusHours(3));
        event(1, "THRESHOLD", "SPO2_LOW", "SPO2", "88", NOW.minusHours(2), NOW.minusHours(2));
        event(1, "DEVICE", "LOW_BATTERY", "OTHER", "03", NOW.minusHours(1), NOW.minusHours(1));

        PersonCard c = maps.card(card(1));

        assertThat(c.name()).isEqualTo("张一");
        assertThat(c.dept()).isEqualTo("综采一队");
        assertThat(c.position().stationName()).isEqualTo("副井口");
        assertThat(c.position().areaName()).isEqualTo("下口区域");
        assertThat(c.position().placed()).isTrue();
        assertThat(c.position().posTime()).isEqualTo(NOW.minusMinutes(3));
        assertThat(c.position().stationEnterTime()).isEqualTo(NOW.minusMinutes(23));
        assertThat(c.position().stationAbnormal()).isTrue();

        assertThat(c.vitals()).containsOnlyKeys("HEART_RATE", "SPO2", "TEMPERATURE", "BLOOD_PRESSURE");
        assertThat(c.vitals().get("SPO2")).isNull();
        assertThat(c.vitals().get("HEART_RATE").value()).isEqualByComparingTo("72");
        assertThat(c.vitals().get("HEART_RATE").collectedAt()).isEqualTo(NOW.minusMinutes(5));
        assertThat(c.vitals().get("HEART_RATE").stale()).isFalse();
        assertThat(c.vitals().get("TEMPERATURE").stale()).isTrue();
        assertThat(c.vitals().get("BLOOD_PRESSURE").value2()).isEqualByComparingTo("79");

        assertThat(c.battery().pct()).isEqualTo(76);
        assertThat(c.battery().time()).isEqualTo(NOW.minusMinutes(2));
        assertThat(c.watch().state()).isEqualTo("ONLINE");

        // 最近 3 条六类告警，设备事件（低电）不算；SOS 不显示值
        assertThat(c.alerts()).extracting(MapViews.RecentAlert::event).containsExactly("血氧偏低", "体温偏高", "SOS 求救");
        assertThat(c.alerts()).extracting(MapViews.RecentAlert::value).containsExactly("88", "37.8", null);
    }

    @Test
    void cardOfPersonNotInWellOrWithoutWatch() {
        person(1, "张一", null);

        PersonCard c = maps.card(card(1));

        assertThat(c.position()).isNull();
        assertThat(c.watch().state()).isEqualTo("UNBOUND");
        assertThat(c.battery()).isNull();
        assertThat(c.vitals().values()).containsOnlyNulls();
        assertThat(c.alerts()).isEmpty();
        assertThatThrownBy(() -> maps.card(card(9)))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ================= 基站列表 =================

    @Test
    void stationListCountsAndOrdersByAreaThenName() {
        String b2 = newStation(AREA_B, "乙2", 0);
        String a1 = newStation(AREA_A, "甲1", 2);
        String b1 = newStation(AREA_B, "乙1", null);
        mark(b2, X0, Y0, "乙0（自定义）");

        StationList all = maps.stations(null);

        assertThat(all.total()).isEqualTo(3);
        assertThat(all.unplaced()).isEqualTo(2);
        // 区域名称排序：-750大巷 在 下口区域 前；同一区域按显示名称
        assertThat(all.list()).extracting(Station::stationCode).containsExactly(b2, b1, a1);
        Station s = all.list().getFirst();
        assertThat(s.name()).isEqualTo("乙0（自定义）");
        assertThat(s.vendorName()).isEqualTo("乙2");
        assertThat(s.placed()).isTrue();
        assertThat(s.version()).isEqualTo(1);
        assertThat(maps.stations(false).list()).extracting(Station::stationCode).containsExactly(b1, a1);
        assertThat(maps.stations(false).total()).isEqualTo(3);
        assertThat(maps.stations(true).list()).extracting(Station::stationCode).containsExactly(b2);
    }

    // ================= 摆放 =================

    @Test
    void placeMoveRenameDeleteEachWriteALogWithBeforeAndAfter() {
        String st = newStation(AREA_A, "甲", 0);

        Station placed = marks.save(st, X0, Y0, null, 0);
        assertThat(placed.placed()).isTrue();
        assertThat(placed.version()).isEqualTo(1);
        assertThat(placed.updatedBy()).isEqualTo("admin");

        BigDecimal x1 = X0.add(new BigDecimal("12.3456"));
        Station moved = marks.save(st, x1, Y0, null, 1);
        assertThat(moved.version()).isEqualTo(2);
        assertThat(moved.x()).isEqualByComparingTo("39482012.846"); // 存 3 位小数

        loginAs("zhang");
        Station renamed = marks.save(st, x1, Y0, "  新副井下口  ", 2);
        assertThat(renamed.name()).isEqualTo("新副井下口");
        assertThat(renamed.displayName()).isEqualTo("新副井下口");
        assertThat(renamed.updatedBy()).isEqualTo("zhang");

        // 没变化：不改版本号、不记日志
        assertThat(marks.save(st, x1, Y0, "新副井下口", 3).version()).isEqualTo(3);

        // 又挪位置又改名：各记一条
        marks.save(st, X0, Y0, null, 3);
        marks.delete(st, 4);
        assertThat(maps.stations(null).list().getFirst().placed()).isFalse();

        assertThat(logs(st)).containsExactly(
                "STATION_DELETE|zhang|{\"x\":39482000.500,\"y\":3852000.500,\"displayName\":null}|",
                "STATION_RENAME|zhang|{\"x\":39482012.846,\"y\":3852000.500,\"displayName\":\"新副井下口\"}"
                        + "|{\"x\":39482000.500,\"y\":3852000.500,\"displayName\":null}",
                "STATION_MOVE|zhang|{\"x\":39482012.846,\"y\":3852000.500,\"displayName\":\"新副井下口\"}"
                        + "|{\"x\":39482000.500,\"y\":3852000.500,\"displayName\":null}",
                "STATION_RENAME|zhang|{\"x\":39482012.846,\"y\":3852000.500,\"displayName\":null}"
                        + "|{\"x\":39482012.846,\"y\":3852000.500,\"displayName\":\"新副井下口\"}",
                "STATION_MOVE|admin|{\"x\":39482000.500,\"y\":3852000.500,\"displayName\":null}"
                        + "|{\"x\":39482012.846,\"y\":3852000.500,\"displayName\":null}",
                "STATION_PLACE|admin||{\"x\":39482000.500,\"y\":3852000.500,\"displayName\":null}");
    }

    @Test
    void staleVersionGetsConflictNamingWhoChangedIt() {
        String st = newStation(AREA_A, "甲", 0);
        marks.save(st, X0, Y0, null, 0);
        loginAs("zhang");
        marks.save(st, X0.add(BigDecimal.ONE), Y0, null, 1);

        loginAs("admin");
        assertConflict(() -> marks.save(st, X0, Y0, null, 1), "这个基站刚刚被 zhang 修改，请刷新");
        assertConflict(() -> marks.delete(st, 1), "这个基站刚刚被 zhang 修改，请刷新");
        // 已经摆放过的又按"第一次摆放"提交
        assertConflict(() -> marks.save(st, X0, Y0, null, 0), "这个基站刚刚被 zhang 修改，请刷新");
        marks.delete(st, 2);
        // 别人已删掉，再改、再删都是冲突
        assertConflict(() -> marks.save(st, X0, Y0, null, 2), "这个基站刚刚被 admin 修改，请刷新");
        assertConflict(() -> marks.delete(st, 2), "这个基站刚刚被 admin 修改，请刷新");
    }

    @Test
    void validation() {
        String st = newStation(AREA_A, "甲", 0);
        assertStatus(() -> marks.save(st, new BigDecimal("39479000"), Y0, null, 0), HttpStatus.BAD_REQUEST);
        assertStatus(() -> marks.save(st, X0, new BigDecimal("3854800"), null, 0), HttpStatus.BAD_REQUEST);
        assertStatus(() -> marks.save(st, X0, Y0, "名".repeat(101), 0), HttpStatus.BAD_REQUEST);
        assertStatus(() -> marks.save(newStationCode(AREA_A), X0, Y0, null, 0), HttpStatus.NOT_FOUND);
        assertStatus(() -> marks.delete(newStationCode(AREA_A), 1), HttpStatus.NOT_FOUND);
        assertThat(logs(st)).isEmpty();
        // 地图范围的边上可以；100 个字可以
        assertThat(marks.save(st, new BigDecimal("39480087.9"), new BigDecimal("3854743.4"), "名".repeat(100), 0)
                .placed()).isTrue();
    }

    @Test
    void logFailureRollsBackTheChange() {
        String st = newStation(AREA_A, "甲", 0);
        // 只在测试用户里临时加一个触发器：写这个基站的操作日志时报错
        jdbc.execute("CREATE OR REPLACE TRIGGER TRG_TEST_OPLOG_FAIL BEFORE INSERT ON SYS_OPERATION_LOG FOR EACH ROW "
                + "WHEN (NEW.TARGET_ID = '" + st + "') BEGIN RAISE_APPLICATION_ERROR(-20999, '模拟日志写失败'); END;");
        try {
            assertThatThrownBy(() -> marks.save(st, X0, Y0, null, 0)).hasMessageContaining("ORA-20999");
        } finally {
            jdbc.execute("DROP TRIGGER TRG_TEST_OPLOG_FAIL");
        }

        assertThat(maps.stations(null).unplaced()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM POS_STATION_MARK WHERE STATION_CODE = ?", st)).isZero();
    }

    @Test
    void operationLogCannotBeUpdatedOrDeleted() {
        String st = newStation(AREA_A, "甲", 0);
        marks.save(st, X0, Y0, null, 0);

        assertThatThrownBy(() -> jdbc.update("UPDATE SYS_OPERATION_LOG SET USERNAME = 'x' WHERE TARGET_ID = ?", st))
                .hasMessageContaining("ORA-20001");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM SYS_OPERATION_LOG WHERE TARGET_ID = ?", st))
                .hasMessageContaining("ORA-20001");
        assertThat(logs(st)).hasSize(1);
    }

    @Test
    void stationLogsArePagedNewestFirstAndFilterable() {
        String a = newStation(AREA_A, "甲", 0);
        String b = newStation(AREA_A, "乙", 0);
        marks.save(a, X0, Y0, null, 0);
        marks.save(b, X0, Y0, null, 0);
        marks.save(a, X0.add(BigDecimal.ONE), Y0, "甲改", 1);

        var page = maps.stationLogs(a, 1, 2);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.list()).extracting(StationLog::action).containsExactly("STATION_RENAME", "STATION_MOVE");
        StationLog first = page.list().getFirst();
        assertThat(first.stationName()).isEqualTo("甲改");
        assertThat(first.before().get("displayName").isNull()).isTrue();
        assertThat(first.after().get("displayName").asString()).isEqualTo("甲改");
        assertThat(first.after().get("x").decimalValue()).isEqualByComparingTo("39482001.5");
        assertThat(maps.stationLogs(a, 2, 2).list()).extracting(StationLog::action).containsExactly("STATION_PLACE");
        // 不传基站编码：全部基站的，最新的在前
        assertThat(maps.stationLogs(null, 1, 1).list().getFirst().stationCode()).isEqualTo(a);
    }

    private static void assertConflict(Runnable r, String message) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(BizException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    private static void assertStatus(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }
}
