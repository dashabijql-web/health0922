package com.xzkj.hv2.watch;

import java.math.BigDecimal;

/**
 * 四项体征，以及用来筛掉明显错误的合理范围（docs/03 第三节）。合理范围不是预警阈值。
 */
public enum Metric {
    HEART_RATE("30", "220"),
    SPO2("50", "100"),
    TEMPERATURE("30.0", "45.0"),
    /** 收缩压的范围；舒张压见 {@link #DIA_MIN}、{@link #DIA_MAX} */
    BLOOD_PRESSURE("60", "260");

    public static final BigDecimal DIA_MIN = new BigDecimal("30");
    public static final BigDecimal DIA_MAX = new BigDecimal("160");

    private final BigDecimal min;
    private final BigDecimal max;

    Metric(String min, String max) {
        this.min = new BigDecimal(min);
        this.max = new BigDecimal(max);
    }

    public boolean inRange(BigDecimal v) {
        return v.compareTo(min) >= 0 && v.compareTo(max) <= 0;
    }
}
