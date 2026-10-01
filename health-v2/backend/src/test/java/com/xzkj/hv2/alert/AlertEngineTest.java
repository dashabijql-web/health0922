package com.xzkj.hv2.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.xzkj.hv2.watch.WatchDbTestBase;

/** 预警（docs/03 第四节）：按岗位类别取阈值、去重、不跨天、设备报警。 */
class AlertEngineTest extends WatchDbTestBase {

    private static final String HEAVY = "62082300920390001";
    private static final String LIGHT = "62082300920390002";
    private static final String UNSORTED = "62082300920390003";
    private static final String IMEI = "869900000000001";

    @Autowired
    private AlertEngine engine;
    @Autowired
    private AlertMapper mapper;

    private void hr(String card, int v, LocalDateTime at) {
        engine.checkVital(card, IMEI, "HEART_RATE", BigDecimal.valueOf(v), null, millis(at));
    }

    private Map<String, Object> onlyEvent(String card) {
        return jdbc.queryForMap("SELECT * FROM ALERT_EVENT WHERE CARD_CODE = ?", card);
    }

    @Test
    void sameHeartRateAlertsOnlyForTheGroupWhoseLimitIsLower() {
        jobGroup("MINING", "采煤机司机", 50, 130);
        jobGroup("OFFICE", "安全员", 50, 110);
        person(HEAVY, "采煤机司机");
        person(LIGHT, "安全员");
        person(UNSORTED, "锚杆工");
        rules.refresh();

        hr(HEAVY, 125, NOW);
        hr(LIGHT, 125, NOW);
        hr(UNSORTED, 125, NOW);

        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT WHERE CARD_CODE = ?", HEAVY)).as("采掘类阈值 130").isZero();
        Map<String, Object> light = onlyEvent(LIGHT);
        assertThat(light.get("CODE")).isEqualTo("HR_HIGH");
        assertThat(light.get("CATEGORY")).isEqualTo("HEART_RATE");
        assertThat(light.get("RULE_GROUP")).isEqualTo("OFFICE");
        assertThat(light.get("VAL_TEXT")).isEqualTo("125");
        assertThat(onlyEvent(UNSORTED).get("RULE_GROUP")).as("工种没归类按 DEFAULT（120）").isEqualTo("DEFAULT");
    }

    @Test
    void groupWithoutThisMetricFallsBackToDefault() {
        jobGroup("MINING", "采煤机司机", 50, 130);
        person(HEAVY, "采煤机司机");
        engine.checkVital(HEAVY, IMEI, "SPO2", new BigDecimal("85"), null, millis(NOW));
        Map<String, Object> e = onlyEvent(HEAVY);
        assertThat(e.get("CODE")).isEqualTo("SPO2_LOW");
        assertThat(e.get("RULE_GROUP")).as("采掘类没配血氧，用 DEFAULT 的").isEqualTo("DEFAULT");
    }

    @Test
    void continuingAbnormalityIsOneEventUntilThirtyMinutesPassSinceLastOccurrence() {
        person(HEAVY, null);
        hr(HEAVY, 130, NOW);
        hr(HEAVY, 135, NOW.plusMinutes(10));
        hr(HEAVY, 132, NOW.plusMinutes(20));
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT")).isEqualTo(1);
        Map<String, Object> e = onlyEvent(HEAVY);
        assertThat(((Number) e.get("OCCUR_COUNT")).intValue()).isEqualTo(3);
        assertThat(e.get("LAST_OCCURRED_AT").toString()).startsWith("2026-09-23 10:20:00");
        assertThat(e.get("VAL_TEXT")).as("保留第一次的值").isEqualTo("130");

        // 距上次发生（10:20）满 30 分钟：新的一条
        hr(HEAVY, 128, NOW.plusMinutes(50));
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT")).isEqualTo(2);
    }

    @Test
    void eventsDoNotCrossMidnight() {
        person(HEAVY, null);
        hr(HEAVY, 130, LocalDateTime.of(2026, 9, 23, 23, 55));
        hr(HEAVY, 130, LocalDateTime.of(2026, 9, 24, 0, 5));
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT")).isEqualTo(2);
    }

    @Test
    void bloodPressureJudgesSystolicAndDiastolicSeparately() {
        person(HEAVY, null);
        engine.checkVital(HEAVY, IMEI, "BLOOD_PRESSURE", new BigDecimal("152.0"), new BigDecimal("98.0"), millis(NOW));
        assertThat(jdbc.queryForList("SELECT CODE FROM ALERT_EVENT ORDER BY CODE", String.class))
                .containsExactly("BP_DIA_HIGH", "BP_SYS_HIGH");
        assertThat(jdbc.queryForList("SELECT DISTINCT VAL_TEXT FROM ALERT_EVENT", String.class)).containsExactly("152/98");
    }

    @Test
    void sosFromUnboundWatchIsRecordedByImei() {
        assertThat(engine.deviceAlarm(null, IMEI, "01", millis(NOW))).isTrue();
        Map<String, Object> e = jdbc.queryForMap("SELECT * FROM ALERT_EVENT");
        assertThat(e.get("CARD_CODE")).isNull();
        assertThat(e.get("DEVICE_IMEI")).isEqualTo(IMEI);
        assertThat(e.get("SRC")).isEqualTo("DEVICE");
        assertThat(e.get("CATEGORY")).isEqualTo("SOS");
        assertThat(((Number) e.get("SEVERITY")).intValue()).isEqualTo(3);
        assertThat(e.get("RULE_GROUP")).isNull();
    }

    @Test
    void sosAndFallDeduplicateForTwoMinutes() {
        person(HEAVY, null);
        engine.deviceAlarm(HEAVY, IMEI, "01", millis(NOW));
        engine.deviceAlarm(HEAVY, IMEI, "01", millis(NOW.plusSeconds(90)));
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT WHERE CODE = 'SOS'")).isEqualTo(1);
        engine.deviceAlarm(HEAVY, IMEI, "01", millis(NOW.plusSeconds(90 + 121)));
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT WHERE CODE = 'SOS'")).isEqualTo(2);
        engine.deviceAlarm(HEAVY, IMEI, "05", millis(NOW));
        engine.deviceAlarm(HEAVY, IMEI, "06", millis(NOW.plusSeconds(30)));
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT WHERE CATEGORY = 'FALL'")).as("05、06 都是跌倒").isEqualTo(1);
    }

    @Test
    void otherDeviceAlarmsAreDeviceEvents() {
        engine.deviceAlarm(null, IMEI, "02", millis(NOW));
        engine.deviceAlarm(null, IMEI, "03", millis(NOW));
        assertThat(jdbc.queryForList("SELECT CODE || ':' || CATEGORY FROM ALERT_EVENT ORDER BY CODE", String.class))
                .containsExactly("LOW_BATTERY:OTHER", "WEAR_OFF:OTHER");
        assertThat(engine.deviceAlarm(null, IMEI, "00", millis(NOW))).as("00 无报警").isTrue();
        assertThat(engine.deviceAlarm(null, IMEI, "77", millis(NOW))).as("不认识的代码").isFalse();
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT")).isEqualTo(2);
    }

    @Test
    void ruleChangesTakeEffectAfterRefresh() {
        person(HEAVY, null);
        hr(HEAVY, 115, NOW);
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT")).isZero();
        jdbc.update("UPDATE ALERT_RULE SET HIGH_LIMIT = 110 WHERE GROUP_CODE = 'DEFAULT' AND METRIC = 'HEART_RATE'");
        rules.refresh();
        hr(HEAVY, 115, NOW.plusMinutes(1));
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT")).isEqualTo(1);
    }

    @Test
    void afterRestartDedupContinuesFromTheTable() {
        person(HEAVY, null);
        hr(HEAVY, 130, NOW);
        // 模拟重启：新的引擎只能从 ALERT_EVENT 读回今天的事件
        AlertEngine restarted = new AlertEngine(mapper, rules, new AlertProperties(30, 2, 30, 60), clock, meters);
        clock.set(NOW.plusMinutes(5));
        restarted.checkVital(HEAVY, IMEI, "HEART_RATE", BigDecimal.valueOf(131), null, millis(NOW.plusMinutes(5)));
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT")).isEqualTo(1);
        assertThat(count("SELECT OCCUR_COUNT FROM ALERT_EVENT")).isEqualTo(2);
    }
}
