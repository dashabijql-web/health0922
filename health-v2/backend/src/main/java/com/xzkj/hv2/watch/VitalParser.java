package com.xzkj.hv2.watch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.xzkj.hv2.watch.WatchMetrics.DropReason;

/**
 * 从体征包里取出有效的指标（docs/03 第一节、第三节）。手表没戴好时仍会上传，值是 0 或占位值：
 * <ul>
 *   <li>某项为 0 或空：丢弃这一项（APHP 里没测的项本来就留空，不算丢弃）；</li>
 *   <li>APHP 出现 0,0,0,95,0.0,0.0（血氧 95 是未佩戴占位值）：整个包丢弃；</li>
 *   <li>超出合理范围：丢弃这一项。血压要收缩压、舒张压都有效才算一条。</li>
 * </ul>
 * 纯函数，不碰数据库和网络。
 */
public final class VitalParser {

    private static final BigDecimal PLACEHOLDER_SPO2 = new BigDecimal("95");

    private VitalParser() {
    }

    /**
     * @param code   协议号：AP49 心率；AP50 体温,电量（电量不采用）；APHT 心率,收缩压,舒张压；
     *               APHP 心率,收缩压,舒张压,血氧,血糖（不用）,体温,……
     * @param params 按逗号拆开的参数
     */
    public static Result parse(String code, String[] params) {
        Result r = new Result();
        switch (code) {
            case "AP49" -> single(r, Metric.HEART_RATE, at(params, 0), true);
            case "AP50" -> single(r, Metric.TEMPERATURE, at(params, 0), true);
            case "APHT" -> {
                single(r, Metric.HEART_RATE, at(params, 0), true);
                pressure(r, at(params, 1), at(params, 2), true);
            }
            case "APHP" -> {
                if (isUnwornPlaceholder(params)) {
                    r.drops.add(DropReason.PLACEHOLDER);
                    return r;
                }
                // APHP 各项不一定都有值，没值的项留空（协议原文），空项不算丢弃
                single(r, Metric.HEART_RATE, at(params, 0), false);
                pressure(r, at(params, 1), at(params, 2), false);
                single(r, Metric.SPO2, at(params, 3), false);
                single(r, Metric.TEMPERATURE, at(params, 5), false);
            }
            default -> {
                // 不是体征包
            }
        }
        return r;
    }

    /** 电量：1–100 有效；0（可能是错报）、空、超范围返回 null，不覆盖已有的有效值。 */
    public static Integer battery(String v) {
        try {
            int b = Integer.parseInt(v.strip());
            return b >= 1 && b <= 100 ? b : null;
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /** APHP 的 0,0,0,95,0.0,0.0：前六项按数值比较，"0" 和 "0.0" 都算 0。 */
    static boolean isUnwornPlaceholder(String[] p) {
        if (p.length < 6) {
            return false;
        }
        BigDecimal[] v = new BigDecimal[6];
        for (int i = 0; i < 6; i++) {
            v[i] = number(p[i]);
            if (v[i] == null) {
                return false;
            }
        }
        return v[0].signum() == 0 && v[1].signum() == 0 && v[2].signum() == 0
                && v[3].compareTo(PLACEHOLDER_SPO2) == 0 && v[4].signum() == 0 && v[5].signum() == 0;
    }

    private static void single(Result r, Metric metric, String raw, boolean emptyIsDrop) {
        if (!emptyIsDrop && isBlank(raw)) {
            return;
        }
        Check c = check(raw);
        if (c.drop != null) {
            r.drops.add(c.drop);
        } else if (!metric.inRange(c.value)) {
            r.drops.add(DropReason.OUT_OF_RANGE);
        } else {
            r.readings.add(new Reading(metric, c.value, null));
        }
    }

    private static void pressure(Result r, String sysRaw, String diaRaw, boolean emptyIsDrop) {
        if (!emptyIsDrop && isBlank(sysRaw) && isBlank(diaRaw)) {
            return;
        }
        Check sys = check(sysRaw);
        Check dia = check(diaRaw);
        if (sys.drop != null || dia.drop != null) {
            r.drops.add(sys.drop != null ? sys.drop : dia.drop);
        } else if (!Metric.BLOOD_PRESSURE.inRange(sys.value)
                || dia.value.compareTo(Metric.DIA_MIN) < 0 || dia.value.compareTo(Metric.DIA_MAX) > 0) {
            r.drops.add(DropReason.OUT_OF_RANGE);
        } else {
            r.readings.add(new Reading(Metric.BLOOD_PRESSURE, sys.value, dia.value));
        }
    }

    private static boolean isBlank(String raw) {
        return raw == null || raw.isBlank();
    }

    private static Check check(String raw) {
        if (isBlank(raw)) {
            return new Check(null, DropReason.ZERO);
        }
        BigDecimal v = number(raw);
        if (v == null) {
            return new Check(null, DropReason.MALFORMED);
        }
        if (v.signum() == 0) {
            return new Check(null, DropReason.ZERO);
        }
        return new Check(v.setScale(1, RoundingMode.HALF_UP), null);
    }

    private static BigDecimal number(String s) {
        try {
            return new BigDecimal(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String at(String[] p, int i) {
        return i < p.length ? p[i] : null;
    }

    private record Check(BigDecimal value, DropReason drop) {
    }

    /** 一个有效指标；血压时 val1 收缩压、val2 舒张压，其余 val2 为 null。 */
    public record Reading(Metric metric, BigDecimal val1, BigDecimal val2) {
    }

    public static final class Result {
        private final List<Reading> readings = new ArrayList<>();
        private final List<DropReason> drops = new ArrayList<>();

        public List<Reading> readings() {
            return readings;
        }

        public List<DropReason> drops() {
            return drops;
        }
    }
}
