package com.xzkj.hv2.alert;

import java.util.List;
import java.util.Map;

/** 事件代码、大屏分类的中文名（docs/03 第一节、第四节），页面列表直接显示。 */
public final class AlertLabels {

    /** 大屏六类告警，顺序就是页面上格子的顺序（docs/09 第三节）。 */
    public static final List<String> CATEGORIES =
            List.of("SOS", "FALL", "HEART_RATE", "BLOOD_PRESSURE", "SPO2", "TEMPERATURE");

    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("HR_HIGH", "心率偏高"),
            Map.entry("HR_LOW", "心率偏低"),
            Map.entry("SPO2_HIGH", "血氧偏高"),
            Map.entry("SPO2_LOW", "血氧偏低"),
            Map.entry("TEMP_HIGH", "体温偏高"),
            Map.entry("TEMP_LOW", "体温偏低"),
            Map.entry("BP_SYS_HIGH", "收缩压偏高"),
            Map.entry("BP_SYS_LOW", "收缩压偏低"),
            Map.entry("BP_DIA_HIGH", "舒张压偏高"),
            Map.entry("BP_DIA_LOW", "舒张压偏低"),
            Map.entry("SOS", "SOS 求救"),
            Map.entry("FALL", "跌倒"),
            Map.entry("LOW_BATTERY", "低电"),
            Map.entry("WEAR_OFF", "脱落"),
            Map.entry("WEAR_REMIND", "佩戴提醒"),
            Map.entry("AFIB", "房颤"),
            Map.entry("REMOVED", "拆卸"),
            Map.entry("INFRARED", "红外"));

    private AlertLabels() {
    }

    /** 不认识的代码原样返回。 */
    public static String codeName(String code) {
        return code == null ? null : CODES.getOrDefault(code, code);
    }
}
