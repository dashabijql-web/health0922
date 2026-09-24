package com.xzkj.health.dto.sleep;

import java.util.List;

public record SleepTrendView(
        List<String> dates,
        List<Double> avgData,
        List<Double> deepSleep,
        List<Double> lightSleep,
        /** 每日平均睡眠评分，评分规则与明细一致：>=8h 90、>=7h 75、>=6h 60、否则 40 */
        List<Double> scores
) {
}
