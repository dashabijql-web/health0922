package com.xzkj.hv2.alert;

import java.util.Map;

/**
 * 手表报警包 AP10 的报警代码（docs/03 第一节）。SOS、跌倒计入大屏六类告警；
 * 其余记为设备事件（CATEGORY=OTHER），在大屏"设备事件"里展示，不计入六类告警。
 */
enum DeviceAlarm {
    NONE(null, null, 0),
    SOS("SOS", "SOS", 3),
    FALL("FALL", "FALL", 3),
    LOW_BATTERY("LOW_BATTERY", "OTHER", 1),
    WEAR_OFF("WEAR_OFF", "OTHER", 1),
    WEAR_REMIND("WEAR_REMIND", "OTHER", 1),
    // 房颤归设备事件还是体征告警待医务人员确认（docs/00 第 18 项），目前按设备事件
    AFIB("AFIB", "OTHER", 1),
    REMOVED("REMOVED", "OTHER", 1),
    INFRARED("INFRARED", "OTHER", 1);

    private static final Map<String, DeviceAlarm> BY_CODE = Map.ofEntries(
            Map.entry("00", NONE),
            Map.entry("01", SOS),
            Map.entry("02", LOW_BATTERY),
            Map.entry("03", WEAR_OFF),
            Map.entry("04", WEAR_REMIND),
            Map.entry("05", FALL),
            Map.entry("06", FALL),
            Map.entry("08", AFIB),
            Map.entry("13", REMOVED),
            Map.entry("14", REMOVED),
            Map.entry("15", REMOVED),
            Map.entry("20", INFRARED));

    final String code;
    final String category;
    final int severity;

    DeviceAlarm(String code, String category, int severity) {
        this.code = code;
        this.category = category;
        this.severity = severity;
    }

    /** 不认识的代码返回 null。 */
    static DeviceAlarm of(String alarmCode) {
        return alarmCode == null ? null : BY_CODE.get(alarmCode.strip());
    }
}
