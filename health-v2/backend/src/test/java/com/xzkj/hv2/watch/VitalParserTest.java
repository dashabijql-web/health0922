package com.xzkj.hv2.watch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.xzkj.hv2.watch.VitalParser.Reading;
import com.xzkj.hv2.watch.WatchMetrics.DropReason;

/** 有效性判断（docs/03 第三节）。 */
class VitalParserTest {

    private static VitalParser.Result parse(String code, String body) {
        return VitalParser.parse(code, body.split(",", -1));
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void heartRate() {
        assertThat(parse("AP49", "68").readings()).containsExactly(new Reading(Metric.HEART_RATE, d("68.0"), null));
    }

    @Test
    void zeroOrEmptyIsDropped() {
        assertThat(parse("AP49", "0").drops()).containsExactly(DropReason.ZERO);
        assertThat(parse("AP49", "").drops()).containsExactly(DropReason.ZERO);
        assertThat(parse("AP50", "0.0,90").drops()).containsExactly(DropReason.ZERO);
        assertThat(parse("APHT", "0,0,0").drops()).containsExactly(DropReason.ZERO, DropReason.ZERO);
    }

    @Test
    void outOfRangeIsDropped() {
        assertThat(parse("AP49", "250").drops()).containsExactly(DropReason.OUT_OF_RANGE);
        assertThat(parse("AP49", "29").drops()).containsExactly(DropReason.OUT_OF_RANGE);
        assertThat(parse("AP50", "45.1,80").drops()).containsExactly(DropReason.OUT_OF_RANGE);
        assertThat(parse("APHT", "70,130,20").drops()).containsExactly(DropReason.OUT_OF_RANGE);
        assertThat(parse("AP49", "abc").drops()).containsExactly(DropReason.MALFORMED);
    }

    @Test
    void temperatureTakesFirstParamAndIgnoresBattery() {
        assertThat(parse("AP50", "36.7,0").readings()).containsExactly(new Reading(Metric.TEMPERATURE, d("36.7"), null));
    }

    @Test
    void heartRateAndPressure() {
        assertThat(parse("APHT", "60,130,85").readings()).containsExactly(
                new Reading(Metric.HEART_RATE, d("60.0"), null),
                new Reading(Metric.BLOOD_PRESSURE, d("130.0"), d("85.0")));
    }

    @Test
    void aphpFullAndPartial() {
        assertThat(parse("APHP", "60,130,85,95,90,36.5,,,,,,,").readings()).containsExactly(
                new Reading(Metric.HEART_RATE, d("60.0"), null),
                new Reading(Metric.BLOOD_PRESSURE, d("130.0"), d("85.0")),
                new Reading(Metric.SPO2, d("95.0"), null),
                new Reading(Metric.TEMPERATURE, d("36.5"), null));
        // 只测了血氧：其余留空，不算丢弃
        VitalParser.Result spo2Only = parse("APHP", ",,,97,,,,,,,,,");
        assertThat(spo2Only.readings()).containsExactly(new Reading(Metric.SPO2, d("97.0"), null));
        assertThat(spo2Only.drops()).isEmpty();
    }

    @Test
    void unwornPlaceholderDropsWholePacket() {
        VitalParser.Result r = parse("APHP", "0,0,0,95,0.0,0.0");
        assertThat(r.readings()).isEmpty();
        assertThat(r.drops()).containsExactly(DropReason.PLACEHOLDER);
        assertThat(parse("APHP", "0,0,0,95,0,0,,,,,,,").drops()).containsExactly(DropReason.PLACEHOLDER);
        // 血氧不是 95 就不是占位包：零值逐项丢弃，血氧 96 照常入库
        assertThat(parse("APHP", "0,0,0,96,0.0,0.0").readings())
                .containsExactly(new Reading(Metric.SPO2, d("96.0"), null));
    }

    @Test
    void battery() {
        assertThat(VitalParser.battery("080")).isEqualTo(80);
        assertThat(VitalParser.battery("000")).isNull();
        assertThat(VitalParser.battery("101")).isNull();
        assertThat(VitalParser.battery("x")).isNull();
    }
}
