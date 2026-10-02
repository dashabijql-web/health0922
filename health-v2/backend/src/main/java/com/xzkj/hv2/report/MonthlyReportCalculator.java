package com.xzkj.hv2.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xzkj.hv2.alert.AlertLabels;
import com.xzkj.hv2.alert.AlertRules;
import com.xzkj.hv2.report.MonthlyReportMapper.NameCount;
import com.xzkj.hv2.report.MonthlyReportMapper.PersonAlertRow;
import com.xzkj.hv2.report.MonthlyReportMapper.PersonMetricRow;
import com.xzkj.hv2.report.MonthlyReportMapper.StepRow;
import com.xzkj.hv2.report.MonthlyReportViews.Alerts;
import com.xzkj.hv2.report.MonthlyReportViews.CategoryCount;
import com.xzkj.hv2.report.MonthlyReportViews.CodeCount;
import com.xzkj.hv2.report.MonthlyReportViews.GroupRule;
import com.xzkj.hv2.report.MonthlyReportViews.MetricRule;
import com.xzkj.hv2.report.MonthlyReportViews.MonthlyReport;
import com.xzkj.hv2.report.MonthlyReportViews.Overview;
import com.xzkj.hv2.report.MonthlyReportViews.RiskPerson;
import com.xzkj.hv2.report.MonthlyReportViews.Risk;
import com.xzkj.hv2.report.MonthlyReportViews.Share;
import com.xzkj.hv2.report.MonthlyReportViews.Stability;
import com.xzkj.hv2.report.MonthlyReportViews.StepRank;
import com.xzkj.hv2.report.MonthlyReportViews.Steps;
import com.xzkj.hv2.report.MonthlyReportViews.UnstablePerson;
import com.xzkj.hv2.report.MonthlyReportViews.VitalStat;
import com.xzkj.hv2.report.MonthlyReportViews.WatchUsage;

/**
 * 算一个月的月报（docs/07 第二部分"三、计算口径"）。所有查询在同一个只读事务里，各页看到的是同一时刻的数据。
 * <p>
 * 稳定性评估、风险职工的参数按岗位类别取（JOB_GROUP、ALERT_RULE），用的是生成报告时这个人所属的类别；
 * 某类别没配的参数用 DEFAULT 的（docs/04 第六节）。正常率的分子分母是日汇总里的 NORMAL_COUNT、SAMPLE_COUNT，
 * 判定用整数交叉相乘比较，不经过浮点数。
 */
@Component
public class MonthlyReportCalculator {

    /** 稳定性评估的三项指标，也是第 5 页的顺序（心率、体温、血氧，同截图 8） */
    public static final List<String> METRICS = List.of("HEART_RATE", "TEMPERATURE", "SPO2");
    static final int TOP_SHARES = 8;
    static final int UNSTABLE_TOP = 10;
    static final int STEP_TOP = 10;
    static final int RISK_TOP = 12;
    static final String OTHER_NAME = "其他";

    /** DEFAULT 类别里也没配时（迁移脚本保证有，不该发生）用 docs/07 的建议值 */
    private static final int FALLBACK_MIN_DAYS = 5;
    private static final int FALLBACK_RISK_EVENTS = 3;
    private static final BigDecimal FALLBACK_STABLE = BigDecimal.valueOf(90);
    private static final BigDecimal FALLBACK_UNSTABLE = BigDecimal.valueOf(70);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportCalculator.class);

    private final MonthlyReportMapper mapper;
    private final int stepMinDays;

    public MonthlyReportCalculator(MonthlyReportMapper mapper, ReportProperties props) {
        this.mapper = mapper;
        this.stepMinDays = props.stepMinDays();
    }

    @Transactional
    public MonthlyReport compute(YearMonth month, LocalDateTime now) {
        mapper.readOnlyTransaction();
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.atStartOfDay();

        Map<String, GroupRule> groups = groupRules();
        List<PersonMetricRow> metrics = mapper.personMetrics(from, to);
        Map<String, Stability> stability = new LinkedHashMap<>();
        Map<String, List<String>> unstableByCard = new HashMap<>();
        for (String m : METRICS) {
            stability.put(m, stability(m, metrics, groups, unstableByCard));
        }

        return new MonthlyReport(month.toString(), now,
                overview(mapper.presenceByJobKind(from, to)),
                watchUsage(mapper.watchUsersByDept(from, to)),
                vitals(mapper.vitals(from, to)),
                alerts(mapper.alertsByCategory(fromTs, toTs), mapper.alertPersons(fromTs, toTs)),
                List.copyOf(groups.values()),
                allNull(stability) ? null : stability,
                steps(mapper.steps(from, to)),
                risk(metrics, mapper.personAlerts(fromTs, toTs), unstableByCard, groups));
    }

    // ================= 第 3、4 页 =================

    private static Overview overview(List<NameCount> rows) {
        int total = rows.stream().mapToInt(NameCount::cnt).sum();
        if (total == 0) {
            return null;
        }
        return new Overview(total, (int) rows.stream().filter(r -> r.name() != null).count(), shares(rows, total));
    }

    private static WatchUsage watchUsage(List<NameCount> rows) {
        int total = rows.stream().mapToInt(NameCount::cnt).sum();
        if (total == 0) {
            return null;
        }
        return new WatchUsage(total, (int) rows.stream().filter(r -> r.name() != null).count(), shares(rows, total));
    }

    /**
     * 饼图和表格的行：有名字的前 8 类（行已按人数从多到少排好），其余合并成"其他"，没填的单独一类放最后。
     */
    static List<Share> shares(List<NameCount> rows, int total) {
        List<Share> out = new ArrayList<>();
        int other = 0;
        Integer unrecorded = null;
        for (NameCount r : rows) {
            if (r.name() == null) {
                unrecorded = r.cnt();
            } else if (out.size() < TOP_SHARES) {
                out.add(new Share(r.name(), "NAMED", r.cnt(), percent(r.cnt(), total)));
            } else {
                other += r.cnt();
            }
        }
        if (other > 0) {
            out.add(new Share(OTHER_NAME, "OTHER", other, percent(other, total)));
        }
        if (unrecorded != null) {
            out.add(new Share(null, "UNRECORDED", unrecorded, percent(unrecorded, total)));
        }
        return out;
    }

    static BigDecimal percent(long part, long total) {
        return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    // ================= 第 5、6 页 =================

    private static Map<String, VitalStat> vitals(List<MonthlyReportMapper.VitalRow> rows) {
        Map<String, VitalStat> out = new LinkedHashMap<>();
        METRICS.forEach(m -> out.put(m, null));
        for (MonthlyReportMapper.VitalRow r : rows) {
            BigDecimal avg = r.weightedSum().divide(BigDecimal.valueOf(r.samples()), 1, RoundingMode.HALF_UP);
            out.put(r.metric(), new VitalStat(r.maxV(), r.minV(), avg, r.samples(), r.persons()));
        }
        return allNull(out) ? null : out;
    }

    private static Alerts alerts(List<MonthlyReportMapper.CategoryRow> rows, int persons) {
        Map<String, MonthlyReportMapper.CategoryRow> byCategory = new HashMap<>();
        rows.forEach(r -> byCategory.put(r.category(), r));
        int events = rows.stream().mapToInt(MonthlyReportMapper.CategoryRow::events).sum();
        if (events == 0) {
            return null;
        }
        List<CategoryCount> list = AlertLabels.CATEGORIES.stream().map(c -> {
            MonthlyReportMapper.CategoryRow r = byCategory.get(c);
            return r == null ? new CategoryCount(c, 0, 0) : new CategoryCount(c, r.persons(), r.events());
        }).toList();
        return new Alerts(persons, events, list);
    }

    // ================= 第 7–10 页 =================

    /** 各岗位类别补齐后的参数，DEFAULT 在最前。 */
    private Map<String, GroupRule> groupRules() {
        Map<String, MonthlyReportMapper.RuleRow> rules = new HashMap<>();
        mapper.rules().forEach(r -> rules.put(r.groupCode() + "|" + r.metric(), r));
        List<MonthlyReportMapper.GroupRow> rows = mapper.groups();
        MonthlyReportMapper.GroupRow def = rows.stream()
                .filter(g -> AlertRules.DEFAULT_GROUP.equals(g.groupCode())).findFirst().orElse(null);
        Map<String, GroupRule> out = new LinkedHashMap<>();
        for (MonthlyReportMapper.GroupRow g : rows) {
            Map<String, MetricRule> metrics = new LinkedHashMap<>();
            for (String m : METRICS) {
                MonthlyReportMapper.RuleRow own = rules.get(g.groupCode() + "|" + m);
                MonthlyReportMapper.RuleRow dflt = rules.get(AlertRules.DEFAULT_GROUP + "|" + m);
                MonthlyReportMapper.RuleRow range = own != null ? own : dflt;
                metrics.put(m, new MetricRule(
                        range == null ? null : range.lowLimit(),
                        range == null ? null : range.highLimit(),
                        range == null || range.enabled() == 1,
                        firstNonNull(own == null ? null : own.stablePct(), dflt == null ? null : dflt.stablePct(),
                                FALLBACK_STABLE, g.groupCode(), m + " STABLE_PCT"),
                        firstNonNull(own == null ? null : own.unstablePct(), dflt == null ? null : dflt.unstablePct(),
                                FALLBACK_UNSTABLE, g.groupCode(), m + " UNSTABLE_PCT")));
            }
            out.put(g.groupCode(), new GroupRule(g.groupCode(), g.groupName(),
                    firstNonNull(g.minEvalDays(), def == null ? null : def.minEvalDays(), FALLBACK_MIN_DAYS,
                            g.groupCode(), "MIN_EVAL_DAYS"),
                    firstNonNull(g.riskEventCount(), def == null ? null : def.riskEventCount(), FALLBACK_RISK_EVENTS,
                            g.groupCode(), "RISK_EVENT_COUNT"),
                    metrics));
        }
        return out;
    }

    private static <T> T firstNonNull(T own, T dflt, T fallback, String group, String what) {
        if (own != null) {
            return own;
        }
        if (dflt != null) {
            return dflt;
        }
        log.warn("月报参数缺失：岗位类别 {} 和 DEFAULT 都没配 {}，用建议值 {}", group, what, fallback);
        return fallback;
    }

    private static GroupRule groupOf(Map<String, GroupRule> groups, String code) {
        GroupRule g = groups.get(code);
        return g != null ? g : groups.get(AlertRules.DEFAULT_GROUP);
    }

    /**
     * 一项指标的稳定性：天数不够的不参与；正常率 ≥ 稳定线算稳定，≥ 不稳定线算波动，其余不稳定。
     * 被评为不稳定的人记进 unstableByCard（第 13 页用）。
     */
    private static Stability stability(String metric, List<PersonMetricRow> rows, Map<String, GroupRule> groups,
                                       Map<String, List<String>> unstableByCard) {
        int persons = 0;
        int stable = 0;
        int fluctuating = 0;
        int notEvaluated = 0;
        List<PersonMetricRow> unstable = new ArrayList<>();
        for (PersonMetricRow r : rows) {
            if (!metric.equals(r.metric())) {
                continue;
            }
            persons++;
            GroupRule g = groupOf(groups, r.groupCode());
            if (r.days() < g.minEvalDays()) {
                notEvaluated++;
                continue;
            }
            MetricRule rule = g.metrics().get(metric);
            if (atLeast(r.normals(), r.samples(), rule.stablePct())) {
                stable++;
            } else if (atLeast(r.normals(), r.samples(), rule.unstablePct())) {
                fluctuating++;
            } else {
                unstable.add(r);
                unstableByCard.computeIfAbsent(r.cardCode(), k -> new ArrayList<>()).add(metric);
            }
        }
        if (persons == 0) {
            return null;
        }
        Comparator<PersonMetricRow> byRate = MonthlyReportCalculator::byRate;
        List<UnstablePerson> list = unstable.stream()
                .sorted(byRate.thenComparing(PersonMetricRow::cardCode))
                .limit(UNSTABLE_TOP)
                .map(r -> new UnstablePerson(r.cardCode(), r.personName(), r.dept(),
                        groupOf(groups, r.groupCode()).name(),
                        BigDecimal.valueOf(r.normals() * 1000 / r.samples(), 1), r.minV(), r.maxV(), r.days()))
                .toList();
        return new Stability(persons, stable, fluctuating, unstable.size(), notEvaluated, list);
    }

    /** normals / samples ≥ pct / 100，即 normals × 100 ≥ pct × samples。 */
    static boolean atLeast(long normals, long samples, BigDecimal pct) {
        return BigDecimal.valueOf(normals).multiply(HUNDRED).compareTo(pct.multiply(BigDecimal.valueOf(samples))) >= 0;
    }

    /** 正常率从低到高（交叉相乘比较）。 */
    private static int byRate(PersonMetricRow a, PersonMetricRow b) {
        return Long.compare(a.normals() * b.samples(), b.normals() * a.samples());
    }

    // ================= 第 11、12 页 =================

    private Steps steps(List<StepRow> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        // 日均从少到多（交叉相乘比较），一样多按卡编码
        Comparator<StepRow> byAvg = (a, b) -> Long.compare(a.total() * b.days(), b.total() * a.days());
        List<StepRow> ranked = rows.stream().filter(r -> r.days() >= stepMinDays)
                .sorted(byAvg.thenComparing(StepRow::cardCode)).toList();
        List<StepRow> high = ranked.stream().sorted(byAvg.reversed().thenComparing(StepRow::cardCode)).toList();
        return new Steps(stepMinDays, ranked.size(), rows.size() - ranked.size(), top(ranked), top(high));
    }

    private static List<StepRank> top(List<StepRow> rows) {
        List<StepRank> out = new ArrayList<>();
        for (int i = 0; i < Math.min(STEP_TOP, rows.size()); i++) {
            StepRow r = rows.get(i);
            // 四舍五入到整数：(2 × total + days) ÷ (2 × days)
            long avg = (2 * r.total() + r.days()) / (2L * r.days());
            out.add(new StepRank(i + 1, r.cardCode(), r.personName(), r.dept(), avg, r.days()));
        }
        return out;
    }

    // ================= 第 13 页 =================

    private static Risk risk(List<PersonMetricRow> metrics, List<PersonAlertRow> alerts,
                             Map<String, List<String>> unstableByCard, Map<String, GroupRule> groups) {
        if (metrics.isEmpty() && alerts.isEmpty()) {
            return null;
        }
        // 每个人的姓名、部门、岗位类别（两个查询里都有，取先见到的）
        Map<String, String[]> who = new HashMap<>();
        metrics.forEach(r -> who.putIfAbsent(r.cardCode(), new String[] {r.personName(), r.dept(), r.groupCode()}));
        alerts.forEach(r -> who.putIfAbsent(r.cardCode(), new String[] {r.personName(), r.dept(), r.groupCode()}));
        Map<String, List<CodeCount>> codes = new HashMap<>();
        for (PersonAlertRow r : alerts) {
            codes.computeIfAbsent(r.cardCode(), k -> new ArrayList<>())
                    .add(new CodeCount(r.code(), AlertLabels.codeName(r.code()), r.events()));
        }

        List<RiskPerson> qualified = new ArrayList<>();
        for (Map.Entry<String, String[]> e : who.entrySet()) {
            String card = e.getKey();
            GroupRule g = groupOf(groups, e.getValue()[2]);
            List<CodeCount> c = codes.getOrDefault(card, List.of()).stream()
                    .sorted(Comparator.comparingInt(CodeCount::count).reversed().thenComparing(CodeCount::code))
                    .toList();
            int count = c.stream().mapToInt(CodeCount::count).sum();
            List<String> unstable = unstableByCard.getOrDefault(card, List.of());
            if (count >= g.riskEventCount() || !unstable.isEmpty()) {
                qualified.add(new RiskPerson(card, e.getValue()[0], e.getValue()[1], g.name(), count,
                        List.copyOf(unstable), c));
            }
        }
        qualified.sort(Comparator.comparingInt(RiskPerson::alertCount).reversed()
                .thenComparing(Comparator.comparingInt((RiskPerson p) -> p.unstable().size()).reversed())
                .thenComparing(RiskPerson::cardCode));
        return new Risk(qualified.size(), qualified.stream().limit(RISK_TOP).toList());
    }

    private static boolean allNull(Map<String, ?> m) {
        return m.values().stream().allMatch(v -> v == null);
    }
}
