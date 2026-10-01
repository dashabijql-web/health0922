package com.xzkj.hv2.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.xzkj.hv2.alert.AlertEngine;
import com.xzkj.hv2.common.api.PageResult;
import com.xzkj.hv2.dashboard.DashboardViews.AlertPerson;
import com.xzkj.hv2.dashboard.DashboardViews.DeviceEvent;
import com.xzkj.hv2.dashboard.DashboardViews.Headcount;
import com.xzkj.hv2.dashboard.DashboardViews.InWellPerson;
import com.xzkj.hv2.dashboard.DashboardViews.Overview;
import com.xzkj.hv2.dashboard.DashboardViews.PortalSummary;
import com.xzkj.hv2.dashboard.DashboardViews.StepRank;

/**
 * 入口页、动态数据页的每个数字（docs/05 第三、四节；docs/08 阶段 3 验收"每个数字都能用一条 SQL 复算"）。
 * 每个数字既和手算的期望值比，也和照 docs/05 口径单独写的一条对照 SQL 比；对照 SQL 不复用 DashboardMapper.xml。
 * 现在是 2026-09-23 10:00（MutableClock），在线窗口 20 分钟，"仍在发生" 30 分钟，定位过期 15 分钟。
 */
class DashboardServiceTest extends DashboardDbTestBase {

    private static final LocalDateTime RYSS_OLD = NOW.minusMinutes(20);
    private static final LocalDateTime RYSS_NEW = NOW.minusMinutes(5);
    private static final String UNBOUND_IMEI = imei(99);

    @Autowired
    private DashboardService service;
    @Autowired
    private AlertEngine alerts;

    /**
     * 场景：
     * <ul>
     *   <li>1–6 号在最新一份 RYSS 里在井下；7 号已出井；8 号只在旧的那份 RYSS 里在井下（不算）；</li>
     *   <li>1、2、6 号手表在线；3 号离线（35 分钟没上行）；4 号的表停用了（算没绑定）；5 号没表；
     *       7 号在线但不在井下；另有一块没绑定的表；</li>
     *   <li>1 号 10 分钟前心率偏高（仍在发生），早上还有一条心率偏低；2 号血氧偏低最后一次在 40 分钟前；
     *       3 号体温偏高 10 分钟前（但离线）；6 号低电（设备事件）；没绑定的表 SOS；2 号昨天也有心率告警。</li>
     * </ul>
     */
    @BeforeEach
    void scene() {
        area("6208230092030001", "一号区域");
        area("6208230092030002", "二号区域");
        for (int i = 1; i <= 8; i++) {
            person(i, i == 5 ? null : "综采队");
        }
        ryss(RYSS_OLD, "DONE");
        ryss(RYSS_NEW, "PARTIAL");
        ryss(NOW.minusMinutes(1), "FAILED");
        for (int i = 1; i <= 6; i++) {
            state(i, 1, i <= 3 ? "6208230092030001" : "6208230092030002", RYSS_NEW);
        }
        state(7, 2, "6208230092030001", RYSS_NEW);
        state(8, 1, "6208230092030001", RYSS_OLD);

        watch(1, card(1), NOW.minusMinutes(1), 1);
        watch(2, card(2), NOW.minusMinutes(19), 1);
        watch(3, card(3), NOW.minusMinutes(35), 1);
        watch(4, card(4), NOW.minusMinutes(1), 0);
        watch(6, card(6), NOW.minusMinutes(2), 1);
        watch(7, card(7), NOW.minusMinutes(1), 1);
        watch(99, null, NOW.minusMinutes(1), 1);

        event(card(1), imei(1), "THRESHOLD", "HR_HIGH", "HEART_RATE", NOW.minusMinutes(25), NOW.minusMinutes(10), 3);
        event(card(1), imei(1), "THRESHOLD", "HR_LOW", "HEART_RATE", NOW.minusHours(3), NOW.minusHours(3), 1);
        event(card(2), imei(2), "THRESHOLD", "SPO2_LOW", "SPO2", NOW.minusHours(2), NOW.minusMinutes(40), 5);
        event(card(3), imei(3), "THRESHOLD", "TEMP_HIGH", "TEMPERATURE", NOW.minusMinutes(10), NOW.minusMinutes(10), 1);
        event(card(6), imei(6), "DEVICE", "LOW_BATTERY", "OTHER", NOW.minusMinutes(8), NOW.minusMinutes(8), 1);
        event(null, UNBOUND_IMEI, "DEVICE", "SOS", "SOS", NOW.minusMinutes(50), NOW.minusMinutes(49), 2);
        event(card(2), imei(2), "THRESHOLD", "HR_HIGH", "HEART_RATE", NOW.minusDays(1), NOW.minusDays(1), 1);
    }

    // ---- 入口页 ----

    @Test
    void portalSummaryMatchesSql() {
        summary(TODAY, 1, "HEART_RATE", "130", "60", "80.00", 10);
        summary(TODAY, 2, "HEART_RATE", "100", "41", "70.50", 30);
        summary(TODAY, 1, "SPO2", "99", "92", "96.00", 4);
        summary(TODAY.minusDays(1), 3, "HEART_RATE", "180", "30", "90.00", 10);
        steps(TODAY, 1, 1200);
        steps(TODAY, 2, 0);
        steps(TODAY, 3, 845);
        steps(TODAY.minusDays(1), 4, 99999);
        watchList(1, "KEY", null, NOW.minusDays(3));
        watchList(2, "KEY", null, NOW.minusDays(1));
        watchList(3, "TODAY", TODAY, NOW.minusHours(1));
        watchList(4, "TODAY", TODAY.minusDays(1), NOW.minusDays(1));

        PortalSummary s = service.portalSummary();

        DashboardViews.Stat hr = s.vitals().get("HEART_RATE");
        assertThat(hr.max()).isEqualByComparingTo("130");
        assertThat(hr.min()).isEqualByComparingTo("41");
        // (80×10 + 70.5×30) ÷ 40 = 72.875 → 72.9
        assertThat(hr.avg()).isEqualByComparingTo("72.9");
        Map<String, Object> hrSql = jdbc.queryForMap("""
                SELECT MAX(MAX_V) MX, MIN(MIN_V) MN, ROUND(SUM(AVG_V * SAMPLE_COUNT) / SUM(SAMPLE_COUNT), 1) AV
                  FROM HEALTH_DAILY_SUMMARY WHERE STAT_DATE = ? AND METRIC = 'HEART_RATE'""", Date.valueOf(TODAY));
        assertThat(hr.max()).isEqualByComparingTo((BigDecimal) hrSql.get("MX"));
        assertThat(hr.min()).isEqualByComparingTo((BigDecimal) hrSql.get("MN"));
        assertThat(hr.avg()).isEqualByComparingTo((BigDecimal) hrSql.get("AV"));
        assertThat(s.vitals().get("SPO2").avg()).isEqualByComparingTo("96.0");
        assertThat(s.vitals()).containsEntry("TEMPERATURE", null);

        DashboardViews.Stat st = s.vitals().get("STEPS");
        Map<String, Object> stSql = jdbc.queryForMap(
                "SELECT MAX(STEPS) MX, MIN(STEPS) MN, ROUND(AVG(STEPS)) AV FROM STEP_DAILY WHERE STAT_DATE = ?",
                Date.valueOf(TODAY));
        assertThat(st.max()).isEqualByComparingTo("1200").isEqualByComparingTo((BigDecimal) stSql.get("MX"));
        assertThat(st.min()).isEqualByComparingTo("0").isEqualByComparingTo((BigDecimal) stSql.get("MN"));
        // (1200 + 0 + 845) ÷ 3 = 681.67 → 682
        assertThat(st.avg()).isEqualByComparingTo("682").isEqualByComparingTo((BigDecimal) stSql.get("AV"));

        assertThat(s.keyPersonCount()).isEqualTo(2)
                .isEqualTo(sqlInt("SELECT COUNT(*) FROM WATCH_LIST WHERE LIST_TYPE = 'KEY'"));
        assertThat(s.todayWatchCount()).isEqualTo(1).isEqualTo(sqlInt(
                "SELECT COUNT(*) FROM WATCH_LIST WHERE LIST_TYPE = 'TODAY' AND EXPIRE_DATE >= ?", Date.valueOf(TODAY)));
        assertThat(s.inWellCount()).isEqualTo(6).isEqualTo(sqlInt("SELECT COUNT(*) FROM V_POS_IN_WELL"));
        assertThat(s.deviceCount()).isEqualTo(6).isEqualTo(sqlInt("SELECT COUNT(*) FROM DEVICE WHERE STATUS = 1"));
        assertThat(s.positioning().dataTime()).isEqualTo(RYSS_NEW);
        assertThat(s.positioning().stale()).isFalse();
        assertThat(s.dataTime()).isEqualTo(NOW.minusMinutes(1));
    }

    // ---- 展示模式 ----

    @Test
    void overviewMatchesSql() {
        jdbc.update("UPDATE METRIC_COUNTER SET TOTAL_COUNT = CASE METRIC WHEN 'HEART_RATE' THEN 5219387 "
                + "WHEN 'SPO2' THEN 602780 WHEN 'TEMPERATURE' THEN 649328 ELSE 0 END");
        jdbc.update("""
                INSERT INTO HEALTH_LATEST (CARD_CODE, METRIC, VAL1, COLLECTED_AT, MSG_ID, DEVICE_IMEI)
                VALUES (?, 'HEART_RATE', 80, ?, 1, ?)""", card(1), ts(NOW.minusMinutes(3)), imei(1));

        Overview o = service.overview();

        assertThat(o.totalCollected()).isEqualTo(6471495L)
                .isEqualTo(jdbc.queryForObject("SELECT SUM(TOTAL_COUNT) FROM METRIC_COUNTER", Long.class));
        assertThat(o.collected()).containsEntry("HEART_RATE", 5219387L).containsEntry("BLOOD_PRESSURE", 0L);
        assertThat(o.dataTime()).isEqualTo(NOW.minusMinutes(3));

        // 六类告警：今天 0 点之后 LAST_OCCURRED_AT、按人（没绑定按设备号）去重
        assertThat(o.alerts()).containsExactly(Map.entry("SOS", 1), Map.entry("FALL", 0), Map.entry("HEART_RATE", 1),
                Map.entry("BLOOD_PRESSURE", 0), Map.entry("SPO2", 1), Map.entry("TEMPERATURE", 1));
        for (String category : o.alerts().keySet()) {
            assertThat(o.alerts().get(category)).as(category).isEqualTo(sqlInt("""
                    SELECT COUNT(DISTINCT NVL(CARD_CODE, DEVICE_IMEI)) FROM ALERT_EVENT
                     WHERE CATEGORY = ? AND LAST_OCCURRED_AT >= ? AND LAST_OCCURRED_AT < ?""",
                    category, ts(TODAY.atStartOfDay()), ts(TODAY.plusDays(1).atStartOfDay())));
        }
        assertThat(o.deviceEventCount()).isEqualTo(1).isEqualTo(sqlInt("""
                SELECT COUNT(*) FROM ALERT_EVENT WHERE SRC = 'DEVICE' AND CATEGORY = 'OTHER'
                   AND LAST_OCCURRED_AT >= ? AND LAST_OCCURRED_AT < ?""",
                ts(TODAY.atStartOfDay()), ts(TODAY.plusDays(1).atStartOfDay())));
    }

    @Test
    void alertListHasOneRowPerPersonAndShowsImeiForUnbound() {
        PageResult<AlertPerson> hr = service.alertPersons("HEART_RATE", 1, 20);
        assertThat(hr.total()).isEqualTo(1L).isEqualTo(service.overview().alerts().get("HEART_RATE").longValue());
        AlertPerson p = hr.list().getFirst();
        assertThat(p.cardCode()).isEqualTo(card(1));
        assertThat(p.name()).isEqualTo("测试1");
        assertThat(p.eventCount()).isEqualTo(2);
        assertThat(p.occurTotal()).isEqualTo(4);
        assertThat(p.lastAt()).isEqualTo(NOW.minusMinutes(10));
        assertThat(p.lastEvent()).isEqualTo("心率偏高");
        assertThat(p.lastValue()).isEqualTo("130");

        AlertPerson sos = service.alertPersons("SOS", 1, 20).list().getFirst();
        assertThat(sos.cardCode()).isNull();
        assertThat(sos.name()).isNull();
        assertThat(sos.deviceImei()).isEqualTo(UNBOUND_IMEI);
        assertThat(sos.lastEvent()).isEqualTo("SOS 求救");
        assertThat(sos.lastValue()).as("设备报警没有体征值").isNull();

        assertThat(service.alertPersons("FALL", 1, 20).list()).isEmpty();
    }

    @Test
    void deviceEventsAreTodaysNewestFirst() {
        // 低电、脱落各来一次：走真正的预警引擎（AP10 的 02、03），未绑定的表记设备号
        alerts.deviceAlarm(null, UNBOUND_IMEI, "02", millis(NOW.minusMinutes(2)));
        alerts.deviceAlarm(card(1), imei(1), "03", millis(NOW.minusMinutes(1)));
        alerts.deviceAlarm(card(1), imei(1), "03", millis(NOW));
        // SOS 不算设备事件（已经在格子里）
        alerts.deviceAlarm(card(2), imei(2), "01", millis(NOW));

        Overview o = service.overview();
        assertThat(o.deviceEventCount()).isEqualTo(3);
        PageResult<DeviceEvent> page = service.deviceEvents(1, 20);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.list()).extracting(DeviceEvent::event).containsExactly("脱落", "低电", "低电");
        DeviceEvent wearOff = page.list().getFirst();
        assertThat(wearOff.name()).isEqualTo("测试1");
        assertThat(wearOff.occurCount()).as("30 分钟内重复只算一条，记次数").isEqualTo(2);
        assertThat(wearOff.lastOccurredAt()).isEqualTo(NOW);
        DeviceEvent unbound = page.list().get(1);
        assertThat(unbound.cardCode()).isNull();
        assertThat(unbound.deviceImei()).isEqualTo(UNBOUND_IMEI);

        assertThat(service.deviceEvents(2, 2).list()).hasSize(1);
    }

    @Test
    void keyPersonsAreNewestFirst() {
        watchList(2, "KEY", null, NOW.minusDays(1));
        watchList(5, "KEY", null, NOW.minusHours(1));
        watchList(3, "TODAY", TODAY, NOW);

        assertThat(service.keyPersons()).extracting(DashboardViews.KeyPerson::cardCode)
                .containsExactly(card(5), card(2));
        assertThat(service.keyPersons().getFirst().dept()).as("没有部门是 null，页面写未录入").isNull();
    }

    // ---- 底部三块 ----

    @Test
    void headcountMatchesSqlAndAddsUp() {
        Headcount h = service.headcount();

        assertThat(h.inWell()).isEqualTo(6).isEqualTo(sqlInt("SELECT COUNT(*) FROM V_POS_IN_WELL"));
        assertThat(h.expected()).isEqualTo(4).isEqualTo(sqlInt("""
                SELECT COUNT(*) FROM V_POS_IN_WELL w JOIN DEVICE d ON d.CARD_CODE = w.CARD_CODE AND d.STATUS = 1"""));
        assertThat(h.online()).isEqualTo(3).isEqualTo(sqlInt("""
                SELECT COUNT(*) FROM V_POS_IN_WELL w JOIN DEVICE d ON d.CARD_CODE = w.CARD_CODE AND d.STATUS = 1
                 WHERE d.LAST_SEEN_AT >= ?""", ts(NOW.minusMinutes(20))));
        assertThat(h.onlineAlert()).isEqualTo(1).isEqualTo(sqlInt("""
                SELECT COUNT(*) FROM V_POS_IN_WELL w JOIN DEVICE d ON d.CARD_CODE = w.CARD_CODE AND d.STATUS = 1
                 WHERE d.LAST_SEEN_AT >= ?
                   AND EXISTS (SELECT 1 FROM ALERT_EVENT e WHERE e.CARD_CODE = w.CARD_CODE
                                  AND e.CATEGORY <> 'OTHER' AND e.LAST_OCCURRED_AT >= ?)""",
                ts(NOW.minusMinutes(20)), ts(NOW.minusMinutes(30))));
        assertThat(h.offline()).isEqualTo(1);
        assertThat(h.onlineNormal()).isEqualTo(2);
        assertThat(h.onlineNormal() + h.onlineAlert()).isEqualTo(h.online());
        assertThat(h.offline()).isEqualTo(h.expected() - h.online());
        assertThat(h.positioning().stale()).isFalse();
    }

    @Test
    void positioningGoesStaleAfterFifteenMinutes() {
        clock.set(RYSS_NEW.plusMinutes(15));
        assertThat(service.headcount().positioning().stale()).isFalse();
        clock.set(RYSS_NEW.plusMinutes(15).plusSeconds(1));
        Headcount h = service.headcount();
        assertThat(h.positioning().stale()).isTrue();
        assertThat(h.positioning().dataTime()).isEqualTo(RYSS_NEW);
        assertThat(h.inWell()).as("过期了仍显示最后一份的人数（页面加横幅）").isEqualTo(6);
    }

    @Test
    void withoutAnyRyssNumbersAreNull() {
        jdbc.update("DELETE FROM POS_INGEST_FILE");
        Headcount h = service.headcount();
        assertThat(h.inWell()).isNull();
        assertThat(h.expected()).isNull();
        assertThat(h.online()).isNull();
        assertThat(h.offline()).isNull();
        assertThat(h.onlineNormal()).isNull();
        assertThat(h.onlineAlert()).isNull();
        assertThat(h.positioning().stale()).isTrue();
        assertThat(service.portalSummary().inWellCount()).isNull();
        assertThat(service.portalSummary().vitals()).containsEntry("HEART_RATE", null).containsEntry("STEPS", null);
        assertThat(service.portalSummary().dataTime()).isNull();
    }

    /**
     * docs/08 阶段 3 验收：一个人心率偏高后恢复正常，30 分钟后从"已上线告警"回到"已上线无告警"，
     * 心率告警格子的今天人数不减少。事件由真正的预警引擎产生。
     */
    @Test
    void highHeartRateRecoversAfterThirtyMinutes() {
        jdbc.update("DELETE FROM ALERT_EVENT");
        rules.refresh();
        LocalDateTime t0 = NOW;
        alerts.checkVital(card(6), imei(6), "HEART_RATE", new BigDecimal("125"), null, millis(t0));
        alerts.checkVital(card(6), imei(6), "HEART_RATE", new BigDecimal("80"), null, millis(t0.plusMinutes(4)));

        Headcount h = service.headcount();
        assertThat(h.onlineAlert()).isEqualTo(1);
        assertThat(h.onlineNormal()).isEqualTo(2);
        assertThat(service.overview().alerts().get("HEART_RATE")).isEqualTo(1);

        // 手表一直在线，之后的心率都正常
        for (int m : new int[] {29, 30, 31}) {
            clock.set(t0.plusMinutes(m));
            for (int i : new int[] {1, 2, 6}) {
                seen(i, t0.plusMinutes(m));
            }
            alerts.checkVital(card(6), imei(6), "HEART_RATE", new BigDecimal("80"), null, millis(t0.plusMinutes(m)));
            h = service.headcount();
            boolean active = m <= 30;
            assertThat(h.onlineAlert()).as("第 %d 分钟", m).isEqualTo(active ? 1 : 0);
            assertThat(h.onlineNormal()).as("第 %d 分钟", m).isEqualTo(active ? 2 : 3);
            assertThat(h.onlineNormal() + h.onlineAlert()).isEqualTo(h.online());
            assertThat(service.overview().alerts().get("HEART_RATE")).as("格子按今天算，不减少").isEqualTo(1);
        }
    }

    @Test
    void inWellPersonsShowWatchStateAndFilter() {
        PageResult<InWellPerson> all = service.inWellPersons(null, 1, 20);
        assertThat(all.total()).isEqualTo(6L).isEqualTo(sqlInt("SELECT COUNT(*) FROM V_POS_IN_WELL").longValue());
        assertThat(all.list()).extracting(InWellPerson::cardCode)
                .containsExactly(card(1), card(2), card(6), card(3), card(4), card(5));
        assertThat(all.list()).extracting(InWellPerson::watchState)
                .containsExactly("ONLINE", "ONLINE", "ONLINE", "OFFLINE", "UNBOUND", "UNBOUND");
        long online = all.list().stream().filter(p -> p.watchState().equals("ONLINE")).count();
        assertThat(online).as("表里在线的人数 = 已上线").isEqualTo(service.headcount().online().longValue());
        InWellPerson first = all.list().getFirst();
        assertThat(first.areaName()).isEqualTo("一号区域");
        assertThat(first.dept()).isEqualTo("综采队");
        assertThat(all.list().get(5).dept()).isNull();

        assertThat(service.inWellPersons("测试3", 1, 20).list()).extracting(InWellPerson::cardCode)
                .containsExactly(card(3));
        // 按卡号（后 5 位）查
        assertThat(service.inWellPersons(card(2).substring(12), 1, 20).list()).extracting(InWellPerson::cardCode)
                .containsExactly(card(2));
        assertThat(service.inWellPersons("测试8", 1, 20).total()).as("8 号不在最新快照里").isZero();
        assertThat(service.inWellPersons("%", 1, 20).total()).as("通配符按普通字符").isZero();

        PageResult<InWellPerson> page2 = service.inWellPersons(" ", 2, 4);
        assertThat(page2.total()).isEqualTo(6L);
        assertThat(page2.list()).extracting(InWellPerson::cardCode).containsExactly(card(4), card(5));
    }

    @Test
    void stepsRankTopN() {
        steps(TODAY, 1, 300);
        steps(TODAY, 2, 1200);
        steps(TODAY, 3, 0);
        steps(TODAY, 4, 1200);
        steps(TODAY.minusDays(1), 5, 99999);

        List<StepRank> top = service.stepsRank(3);
        assertThat(top).extracting(StepRank::cardCode).containsExactly(card(2), card(4), card(1));
        assertThat(top).extracting(StepRank::rank).containsExactly(1, 2, 3);
        assertThat(top).extracting(StepRank::barPct).containsExactly(100, 100, 25);
        assertThat(jdbc.queryForList("""
                SELECT s.CARD_CODE FROM STEP_DAILY s JOIN POS_PERSON p ON p.CARD_CODE = s.CARD_CODE
                 WHERE s.STAT_DATE = ? ORDER BY s.STEPS DESC, p.PERSON_NAME FETCH FIRST 3 ROWS ONLY""",
                String.class, Date.valueOf(TODAY)))
                .containsExactlyElementsOf(top.stream().map(StepRank::cardCode).toList());

        List<StepRank> all = service.stepsRank(10);
        assertThat(all).as("今天有记录的都列出，0 步也列；昨天的不列").hasSize(4);
        assertThat(all.getLast().steps()).isZero();
    }

    @Test
    void headcountSeriesIsOneDay() {
        headcountPoint(TODAY.minusDays(1).atTime(23, 55), 9);
        headcountPoint(TODAY.atTime(0, 0), 10);
        headcountPoint(TODAY.atTime(0, 5), 11);
        headcountPoint(TODAY.atTime(9, 55), 6);
        headcountPoint(TODAY.plusDays(1).atStartOfDay(), 3);

        DashboardViews.HeadcountSeries s = service.headcountSeries(TODAY);
        assertThat(s.date()).isEqualTo("2026-09-23");
        assertThat(s.gapMinutes()).isEqualTo(15);
        assertThat(s.points()).extracting(DashboardViews.HeadcountSeries.Point::inWell).containsExactly(10, 11, 6);
        assertThat(s.points().getFirst().time()).isEqualTo(TODAY.atStartOfDay());
        assertThat(service.headcountSeries(TODAY.minusDays(2)).points()).isEmpty();
    }
}
