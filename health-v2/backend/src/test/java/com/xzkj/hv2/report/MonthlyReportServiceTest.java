package com.xzkj.hv2.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.report.MonthlyReportViews.CategoryCount;
import com.xzkj.hv2.report.MonthlyReportViews.GroupRule;
import com.xzkj.hv2.report.MonthlyReportViews.MonthlyReport;
import com.xzkj.hv2.report.MonthlyReportViews.Months;
import com.xzkj.hv2.report.MonthlyReportViews.RiskPerson;
import com.xzkj.hv2.report.MonthlyReportViews.Share;
import com.xzkj.hv2.report.MonthlyReportViews.Stability;
import com.xzkj.hv2.report.MonthlyReportViews.StepRank;
import com.xzkj.hv2.report.MonthlyReportViews.UnstablePerson;
import com.xzkj.hv2.report.MonthlyReportViews.VitalStat;

/**
 * 月度汇总（docs/07 第二部分，docs/08 阶段 6 验收），连 Oracle 测试用户。
 * 第一个测试造一个月的随机数据，每一页的数字都用测试里另写的 SQL 复算一遍再比较；其余测试看边界和规则。
 */
class MonthlyReportServiceTest extends ReportDbTestBase {

    // 前后都带空格：文本块（"""）会去掉行尾空格，拼接时不能指望它
    private static final String IN_AUG = " STAT_DATE >= DATE '2026-08-01' AND STAT_DATE < DATE '2026-09-01' ";
    private static final String EVENT_IN_AUG = " OCCURRED_AT >= TIMESTAMP '2026-08-01 00:00:00' "
            + "AND OCCURRED_AT < TIMESTAMP '2026-09-01 00:00:00' AND CATEGORY <> 'OTHER' ";
    private static final String[] JOBS = {"采煤机司机", "支架工", "输送机司机", "掘进机司机", "锚杆工", "电钳工",
        "变电所值班员", "皮带司机", "绞车司机", "信号工", "瓦斯检查工"};
    private static final String[] DEPTS = {"综采一队", "综采二队", "掘进一队", "机电队", "运输队", "通风队"};
    private static final String[] CODES = {"HR_HIGH", "HR_LOW", "SPO2_LOW", "TEMP_HIGH", "BP_SYS_HIGH", "SOS", "FALL",
        "LOW_BATTERY", "WEAR_OFF"};
    private static final String[] CATS = {"HEART_RATE", "HEART_RATE", "SPO2", "TEMPERATURE", "BLOOD_PRESSURE", "SOS",
        "FALL", "OTHER", "OTHER"};

    // ================= 每页数字都能用 SQL 复算 =================

    /**
     * 60 个人：11 种工种（超过 8 种，有"其他"），一部分工种没设置、部门没录入；采煤机司机、支架工归到"重体力"类别
     * （稳定线 80、不稳定线 60、最少 3 天、风险 5 条），锚杆工归到只配了范围、没配月报参数的类别（用 DEFAULT 的）。
     * 7 月 31 日、9 月 1 日也有数据，不能算进 8 月。
     */
    private void randomMonth() {
        group("HEAVY", 3, 5, 80, 60, "采煤机司机", "支架工");
        group("LIGHT", null, null, null, null, "锚杆工");
        Random rnd = new Random(20260801);
        for (int i = 1; i <= 60; i++) {
            String job = rnd.nextInt(8) == 0 ? null : JOBS[rnd.nextInt(JOBS.length)];
            String dept = rnd.nextInt(10) == 0 ? null : DEPTS[rnd.nextInt(DEPTS.length)];
            person(i, "测试" + i, job, dept);
            int presenceDays = rnd.nextInt(5) == 0 ? 0 : 1 + rnd.nextInt(25);
            for (int d = 1; d <= presenceDays; d++) {
                presence(i, aug(d));
            }
            // 有的人整月没戴表，有的只戴几天（不够评估）
            int wornDays = i % 9 == 0 ? 0 : i % 7 == 0 ? 1 + rnd.nextInt(4) : 5 + rnd.nextInt(26);
            double badRate = rnd.nextInt(4) == 0 ? 0.25 + rnd.nextDouble() * 0.3 : rnd.nextDouble() * 0.15;
            for (int d = 1; d <= wornDays; d++) {
                for (String m : new String[] {"HEART_RATE", "TEMPERATURE", "SPO2", "BP_SYS"}) {
                    if (rnd.nextInt(10) == 0) {
                        continue;
                    }
                    int samples = 20 + rnd.nextInt(180);
                    int normals = samples - (int) Math.round(samples * badRate * rnd.nextDouble() * 2);
                    String[] v = switch (m) {
                        case "HEART_RATE" -> new String[] {String.valueOf(100 + rnd.nextInt(60)),
                            String.valueOf(45 + rnd.nextInt(20)), (70 + rnd.nextInt(20)) + "." + rnd.nextInt(100)};
                        case "TEMPERATURE" -> new String[] {"37." + rnd.nextInt(10), "36." + rnd.nextInt(5),
                            "36." + (50 + rnd.nextInt(40))};
                        case "SPO2" -> new String[] {String.valueOf(97 + rnd.nextInt(3)),
                            String.valueOf(85 + rnd.nextInt(10)), "9" + (5 + rnd.nextInt(3)) + "." + rnd.nextInt(100)};
                        default -> new String[] {"150", "95", "120.5"};
                    };
                    summary(i, aug(d), m, v[0], v[1], v[2], samples, Math.max(0, normals));
                }
                if (rnd.nextInt(6) != 0) {
                    steps(i, aug(d), rnd.nextInt(30000));
                }
            }
            int events = rnd.nextInt(2) == 0 ? rnd.nextInt(8) : 0;
            for (int e = 0; e < events; e++) {
                int c = rnd.nextInt(CODES.length);
                event(i, CODES[c], CATS[c], aug(1 + rnd.nextInt(31)).atTime(rnd.nextInt(24), rnd.nextInt(60)));
            }
        }
        // 没绑定的手表：没有卡编码，按设备号算一个人
        event(null, "SOS", "SOS", aug(9).atTime(9, 0));
        event(null, "HR_HIGH", "HEART_RATE", aug(10).atTime(9, 0));
        // 8 月以外的数据
        presence(1, LocalDate.of(2026, 7, 31));
        presence(2, LocalDate.of(2026, 9, 1));
        hr(1, LocalDate.of(2026, 7, 31), 100, 10);
        hr(2, LocalDate.of(2026, 9, 1), 100, 10);
        steps(3, LocalDate.of(2026, 9, 1), 99999);
        event(4, "SOS", "SOS", LocalDateTime.of(2026, 9, 1, 0, 0));
        event(4, "SOS", "SOS", LocalDateTime.of(2026, 7, 31, 23, 59, 59));
    }

    @Test
    void everyPageMatchesSqlRecomputation() {
        randomMonth();

        MonthlyReport r = august();

        assertThat(r.month()).isEqualTo("2026-08");
        assertThat(r.generatedAt()).isEqualTo(NOW);

        // ---- 第 3 页：概述 ----
        assertThat(r.overview().workerCount()).isPositive().isEqualTo(count(
                "SELECT COUNT(DISTINCT CARD_CODE) FROM POS_PRESENCE_DAILY WHERE " + IN_AUG));
        assertThat(r.overview().jobKindCount()).isEqualTo(count("""
                SELECT COUNT(DISTINCT p.JOB_KIND) FROM POS_PRESENCE_DAILY d JOIN POS_PERSON p
                    ON p.CARD_CODE = d.CARD_CODE WHERE""" + IN_AUG));
        assertThat(r.overview().jobKindCount()).as("工种超过 8 种，才测得到'其他'").isGreaterThan(8);
        assertSharesMatch(r.overview().byJobKind(), "p.JOB_KIND",
                "(SELECT DISTINCT CARD_CODE FROM POS_PRESENCE_DAILY WHERE " + IN_AUG + ")");

        // ---- 第 4 页：手表使用 ----
        assertThat(r.watchUsage().userCount()).isEqualTo(count(
                "SELECT COUNT(DISTINCT CARD_CODE) FROM HEALTH_DAILY_SUMMARY WHERE " + IN_AUG));
        assertThat(r.watchUsage().deptCount()).isEqualTo(count("""
                SELECT COUNT(DISTINCT p.DEPT) FROM HEALTH_DAILY_SUMMARY h JOIN POS_PERSON p
                    ON p.CARD_CODE = h.CARD_CODE WHERE""" + IN_AUG));
        assertSharesMatch(r.watchUsage().byDept(), "p.DEPT",
                "(SELECT DISTINCT CARD_CODE FROM HEALTH_DAILY_SUMMARY WHERE " + IN_AUG + ")");

        // ---- 第 5 页：体征指标 ----
        assertThat(r.vitals()).containsOnlyKeys("HEART_RATE", "TEMPERATURE", "SPO2");
        for (String m : MonthlyReportCalculator.METRICS) {
            Map<String, Object> s = jdbc.queryForMap("""
                    SELECT MAX(MAX_V) MX, MIN(MIN_V) MN, ROUND(SUM(AVG_V * SAMPLE_COUNT) / SUM(SAMPLE_COUNT), 1) AV,
                           SUM(SAMPLE_COUNT) SC, COUNT(DISTINCT CARD_CODE) PC
                      FROM HEALTH_DAILY_SUMMARY WHERE METRIC = ? AND """ + IN_AUG, m);
            VitalStat v = r.vitals().get(m);
            assertThat(v.max()).as(m).isEqualByComparingTo((BigDecimal) s.get("MX"));
            assertThat(v.min()).as(m).isEqualByComparingTo((BigDecimal) s.get("MN"));
            assertThat(v.avg()).as(m).isEqualByComparingTo((BigDecimal) s.get("AV"));
            assertThat(v.samples()).as(m).isEqualTo(((BigDecimal) s.get("SC")).longValue());
            assertThat(v.persons()).as(m).isEqualTo(((BigDecimal) s.get("PC")).intValue());
        }

        // ---- 第 6 页：告警 ----
        assertThat(r.alerts().events()).isEqualTo(count("SELECT COUNT(*) FROM ALERT_EVENT WHERE " + EVENT_IN_AUG));
        assertThat(r.alerts().persons()).isEqualTo(count(
                "SELECT COUNT(DISTINCT NVL(CARD_CODE, DEVICE_IMEI)) FROM ALERT_EVENT WHERE " + EVENT_IN_AUG));
        assertThat(r.alerts().byCategory()).extracting(CategoryCount::category)
                .containsExactly("SOS", "FALL", "HEART_RATE", "BLOOD_PRESSURE", "SPO2", "TEMPERATURE");
        for (CategoryCount c : r.alerts().byCategory()) {
            assertThat(c.events()).as(c.category()).isEqualTo(count(
                    "SELECT COUNT(*) FROM ALERT_EVENT WHERE CATEGORY = ? AND " + EVENT_IN_AUG, c.category()));
            assertThat(c.persons()).as(c.category()).isEqualTo(count("SELECT COUNT(DISTINCT NVL(CARD_CODE, "
                    + "DEVICE_IMEI)) FROM ALERT_EVENT WHERE CATEGORY = ? AND " + EVENT_IN_AUG, c.category()));
        }

        // ---- 第 8–10 页：稳定性 ----
        Map<String, Set<String>> unstableSql = new LinkedHashMap<>();
        for (String m : MonthlyReportCalculator.METRICS) {
            Map<String, Integer> byStatus = new LinkedHashMap<>();
            Set<String> unstable = new HashSet<>();
            for (Map<String, Object> row : jdbc.queryForList(STABILITY_SQL + " SELECT CARD_CODE, ST FROM c", m)) {
                byStatus.merge((String) row.get("ST"), 1, Integer::sum);
                if ("UNSTABLE".equals(row.get("ST"))) {
                    unstable.add((String) row.get("CARD_CODE"));
                }
            }
            unstableSql.put(m, unstable);
            Stability s = r.stability().get(m);
            assertThat(s.stable()).as(m).isEqualTo(byStatus.getOrDefault("STABLE", 0));
            assertThat(s.fluctuating()).as(m).isEqualTo(byStatus.getOrDefault("FLUCT", 0));
            assertThat(s.unstable()).as(m).isPositive().isEqualTo(byStatus.getOrDefault("UNSTABLE", 0));
            assertThat(s.notEvaluated()).as(m).isPositive().isEqualTo(byStatus.getOrDefault("NOT", 0));
            assertThat(s.persons()).as(m).isEqualTo(s.stable() + s.fluctuating() + s.unstable() + s.notEvaluated());

            List<Map<String, Object>> top = jdbc.queryForList(STABILITY_SQL + """
                    SELECT c.CARD_CODE, TRUNC(c.N * 1000 / c.S) / 10 PCT, c.MN, c.MX, c.DAYS, g.GROUP_NAME
                      FROM c JOIN JOB_GROUP g ON g.GROUP_CODE = c.G
                     WHERE c.ST = 'UNSTABLE' ORDER BY c.N / c.S, c.CARD_CODE FETCH FIRST 10 ROWS ONLY""", m);
            assertThat(s.unstableList()).hasSize(top.size());
            for (int k = 0; k < top.size(); k++) {
                UnstablePerson u = s.unstableList().get(k);
                Map<String, Object> t = top.get(k);
                assertThat(u.cardCode()).isEqualTo(t.get("CARD_CODE"));
                assertThat(u.normalPct()).isEqualByComparingTo((BigDecimal) t.get("PCT"));
                assertThat(u.min()).isEqualByComparingTo((BigDecimal) t.get("MN"));
                assertThat(u.max()).isEqualByComparingTo((BigDecimal) t.get("MX"));
                assertThat(u.days()).isEqualTo(((BigDecimal) t.get("DAYS")).intValue());
                assertThat(u.groupName()).isEqualTo(t.get("GROUP_NAME"));
                assertThat(u.name()).isEqualTo(jdbc.queryForObject(
                        "SELECT PERSON_NAME FROM POS_PERSON WHERE CARD_CODE = ?", String.class, u.cardCode()));
            }
        }

        // ---- 第 11、12 页：运动量 ----
        String stepSql = """
                SELECT CARD_CODE, ROUND(SUM(STEPS) / COUNT(*)) AVG_STEPS, COUNT(*) DAYS
                  FROM STEP_DAILY WHERE """ + IN_AUG + """
                 GROUP BY CARD_CODE HAVING COUNT(*) >= 5
                 ORDER BY SUM(STEPS) / COUNT(*) %s, CARD_CODE FETCH FIRST 10 ROWS ONLY""";
        assertStepsMatch(r.steps().low(), jdbc.queryForList(stepSql.formatted("ASC")));
        assertStepsMatch(r.steps().high(), jdbc.queryForList(stepSql.formatted("DESC")));
        assertThat(r.steps().ranked()).isEqualTo(count("SELECT COUNT(*) FROM (SELECT CARD_CODE FROM STEP_DAILY WHERE "
                + IN_AUG + " GROUP BY CARD_CODE HAVING COUNT(*) >= 5)"));
        assertThat(r.steps().excluded()).isPositive().isEqualTo(count("SELECT COUNT(*) FROM (SELECT CARD_CODE FROM "
                + "STEP_DAILY WHERE " + IN_AUG + " GROUP BY CARD_CODE HAVING COUNT(*) < 5)"));
        assertThat(r.steps().minDays()).isEqualTo(5);

        // ---- 第 13 页：风险职工 ----
        Set<String> expected = new HashSet<>(jdbc.queryForList("""
                SELECT e.CARD_CODE
                  FROM ALERT_EVENT e
                  JOIN POS_PERSON p ON p.CARD_CODE = e.CARD_CODE
                  LEFT JOIN JOB_KIND_GROUP k ON k.JOB_KIND = p.JOB_KIND
                  JOIN JOB_GROUP g ON g.GROUP_CODE = NVL(k.GROUP_CODE, 'DEFAULT')
                 WHERE """ + EVENT_IN_AUG + """
                 GROUP BY e.CARD_CODE, g.RISK_EVENT_COUNT
                HAVING COUNT(*) >= NVL(g.RISK_EVENT_COUNT,
                                       (SELECT RISK_EVENT_COUNT FROM JOB_GROUP WHERE GROUP_CODE = 'DEFAULT'))""",
                String.class));
        unstableSql.values().forEach(expected::addAll);
        assertThat(r.risk().total()).isEqualTo(expected.size()).isGreaterThan(MonthlyReportCalculator.RISK_TOP);
        assertThat(r.risk().list()).hasSize(MonthlyReportCalculator.RISK_TOP);
        for (RiskPerson p : r.risk().list()) {
            assertThat(expected).contains(p.cardCode());
            assertThat(p.alertCount()).isEqualTo(count("SELECT COUNT(*) FROM ALERT_EVENT WHERE CARD_CODE = ? AND "
                    + EVENT_IN_AUG, p.cardCode()));
            assertThat(p.alerts().stream().mapToInt(MonthlyReportViews.CodeCount::count).sum())
                    .isEqualTo(p.alertCount());
            List<String> unstable = new ArrayList<>();
            unstableSql.forEach((m, cards) -> {
                if (cards.contains(p.cardCode())) {
                    unstable.add(m);
                }
            });
            assertThat(p.unstable()).as(p.cardCode()).isEqualTo(unstable);
        }
        // 前 12 人：告警次数多的在前，同样多时"不稳定"项数多的在前
        for (int k = 1; k < r.risk().list().size(); k++) {
            RiskPerson a = r.risk().list().get(k - 1);
            RiskPerson b = r.risk().list().get(k);
            assertThat(a.alertCount() > b.alertCount()
                    || a.alertCount() == b.alertCount() && a.unstable().size() >= b.unstable().size()).isTrue();
        }
    }

    /**
     * 每人一项指标的评估状态（c.ST：NOT 不参与 / STABLE / FLUCT 波动 / UNSTABLE），参数按现在所属的岗位类别，
     * 没配的用 DEFAULT 的。? 是指标。
     */
    private static final String STABILITY_SQL = """
            WITH pm AS (
                SELECT CARD_CODE, METRIC M, COUNT(*) DAYS, SUM(SAMPLE_COUNT) S, SUM(NORMAL_COUNT) N,
                       MIN(MIN_V) MN, MAX(MAX_V) MX
                  FROM HEALTH_DAILY_SUMMARY
                 WHERE METRIC = ? AND SAMPLE_COUNT > 0 AND """ + IN_AUG + """
                 GROUP BY CARD_CODE, METRIC),
            par AS (
                SELECT pm.*, jg.GROUP_CODE G,
                       COALESCE(jg.MIN_EVAL_DAYS, dg.MIN_EVAL_DAYS) MIND,
                       COALESCE(r.STABLE_PCT, dr.STABLE_PCT) SP,
                       COALESCE(r.UNSTABLE_PCT, dr.UNSTABLE_PCT) UP
                  FROM pm
                  LEFT JOIN POS_PERSON p ON p.CARD_CODE = pm.CARD_CODE
                  LEFT JOIN JOB_KIND_GROUP k ON k.JOB_KIND = p.JOB_KIND
                  JOIN JOB_GROUP jg ON jg.GROUP_CODE = NVL(k.GROUP_CODE, 'DEFAULT')
                  JOIN JOB_GROUP dg ON dg.GROUP_CODE = 'DEFAULT'
                  LEFT JOIN ALERT_RULE r ON r.GROUP_CODE = jg.GROUP_CODE AND r.METRIC = pm.M
                  JOIN ALERT_RULE dr ON dr.GROUP_CODE = 'DEFAULT' AND dr.METRIC = pm.M),
            c AS (
                SELECT par.*, CASE WHEN DAYS < MIND THEN 'NOT'
                                   WHEN N * 100 >= SP * S THEN 'STABLE'
                                   WHEN N * 100 >= UP * S THEN 'FLUCT'
                                   ELSE 'UNSTABLE' END ST
                  FROM par)
            """;

    /** 饼图和表格：按人数从多到少（同样多按拼音）前 8 个有名字的，其余合并"其他"，没填的单独一类。 */
    private void assertSharesMatch(List<Share> shares, String column, String people) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT %1$s NAME, COUNT(*) CNT, ROUND(COUNT(*) * 100 / SUM(COUNT(*)) OVER (), 2) PCT
                  FROM %2$s x JOIN POS_PERSON p ON p.CARD_CODE = x.CARD_CODE
                 GROUP BY %1$s
                 ORDER BY CNT DESC, NLSSORT(%1$s, 'NLS_SORT=SCHINESE_PINYIN_M') NULLS LAST, %1$s""".formatted(column,
                people));
        int total = rows.stream().mapToInt(m -> ((BigDecimal) m.get("CNT")).intValue()).sum();
        List<Map<String, Object>> named = rows.stream().filter(m -> m.get("NAME") != null).toList();
        Map<String, Object> unrecorded = rows.stream().filter(m -> m.get("NAME") == null).findFirst().orElse(null);
        int k = 0;
        for (; k < Math.min(8, named.size()); k++) {
            Share s = shares.get(k);
            assertThat(s.kind()).isEqualTo("NAMED");
            assertThat(s.name()).isEqualTo(named.get(k).get("NAME"));
            assertThat(s.count()).isEqualTo(((BigDecimal) named.get(k).get("CNT")).intValue());
            assertThat(s.percent()).isEqualByComparingTo((BigDecimal) named.get(k).get("PCT"));
        }
        int other = named.stream().skip(8).mapToInt(m -> ((BigDecimal) m.get("CNT")).intValue()).sum();
        if (other > 0) {
            Share s = shares.get(k++);
            assertThat(s.kind()).isEqualTo("OTHER");
            assertThat(s.name()).isEqualTo("其他");
            assertThat(s.count()).isEqualTo(other);
            assertThat(s.percent()).isEqualByComparingTo(MonthlyReportCalculator.percent(other, total));
        }
        if (unrecorded != null) {
            Share s = shares.get(k++);
            assertThat(s.kind()).isEqualTo("UNRECORDED");
            assertThat(s.name()).isNull();
            assertThat(s.count()).isEqualTo(((BigDecimal) unrecorded.get("CNT")).intValue());
        }
        assertThat(shares).hasSize(k);
        assertThat(shares.stream().mapToInt(Share::count).sum()).isEqualTo(total);
    }

    private static void assertStepsMatch(List<StepRank> ranks, List<Map<String, Object>> rows) {
        assertThat(ranks).hasSize(rows.size());
        for (int k = 0; k < rows.size(); k++) {
            assertThat(ranks.get(k).rank()).isEqualTo(k + 1);
            assertThat(ranks.get(k).cardCode()).isEqualTo(rows.get(k).get("CARD_CODE"));
            assertThat(ranks.get(k).avgSteps()).isEqualTo(((BigDecimal) rows.get(k).get("AVG_STEPS")).longValue());
            assertThat(ranks.get(k).days()).isEqualTo(((BigDecimal) rows.get(k).get("DAYS")).intValue());
        }
    }

    // ================= 没有数据 =================

    @Test
    void monthWithoutDataHasNullSectionsButKeepsRules() {
        person(1, "张三", "电工", "机电队");
        hr(1, aug(5), 10, 10);

        MonthlyReport july = service.monthly(YearMonth.of(2026, 7));

        assertThat(july.overview()).isNull();
        assertThat(july.watchUsage()).isNull();
        assertThat(july.vitals()).isNull();
        assertThat(july.alerts()).isNull();
        assertThat(july.stability()).isNull();
        assertThat(july.steps()).isNull();
        assertThat(july.risk()).isNull();
        assertThat(july.rules()).extracting(GroupRule::code).containsExactly("DEFAULT");
        assertThat(july.rules().get(0).minEvalDays()).isEqualTo(5);
        assertThat(july.rules().get(0).metrics().get("SPO2").high()).as("血氧没有上限").isNull();
    }

    @Test
    void sectionsWithoutDataAreNullIndividually() {
        person(1, "张三", "电工", "机电队");
        hr(1, aug(5), 10, 10);
        event(1, "LOW_BATTERY", "OTHER", aug(5).atTime(9, 0));

        MonthlyReport r = august();

        assertThat(r.overview()).as("没有出入井记录").isNull();
        assertThat(r.watchUsage().userCount()).isEqualTo(1);
        assertThat(r.vitals().get("HEART_RATE")).isNotNull();
        assertThat(r.vitals().get("SPO2")).isNull();
        assertThat(r.alerts()).as("只有低电（设备事件）").isNull();
        assertThat(r.stability().get("HEART_RATE").notEvaluated()).isEqualTo(1);
        assertThat(r.stability().get("TEMPERATURE")).isNull();
        assertThat(r.steps()).isNull();
        assertThat(r.risk().total()).as("评估了，没有人符合").isZero();
        assertThat(r.risk().list()).isEmpty();
    }

    @Test
    void monthsListOnlyMonthsWithDataAndDefaultToPreviousMonth() {
        person(1, "张三", "电工", "机电队");
        assertThat(service.months()).isEqualTo(new Months(List.of(), null));

        presence(1, LocalDate.of(2026, 5, 20));
        steps(1, LocalDate.of(2026, 7, 31), 100);
        event(1, "LOW_BATTERY", "OTHER", LocalDateTime.of(2026, 6, 3, 9, 0));
        event(1, "FALL", "FALL", LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(service.months()).as("6 月只有设备事件不算；上个月（8 月）没有数据时默认最近的")
                .isEqualTo(new Months(List.of("2026-05", "2026-07", "2026-09"), "2026-09"));

        hr(1, aug(31), 10, 10);
        assertThat(service.months()).isEqualTo(new Months(List.of("2026-05", "2026-07", "2026-08", "2026-09"),
                "2026-08"));

        presence(1, LocalDate.of(2026, 10, 1));
        assertThat(service.months().months()).as("还没到的月份不列").doesNotContain("2026-10");
    }

    @Test
    void futureMonthIsRejected() {
        assertThatThrownBy(() -> service.monthly(YearMonth.of(2026, 10)))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(service.monthly(YearMonth.of(2026, 9)).month()).isEqualTo("2026-09");
    }

    // ================= 岗位类别 =================

    /** docs/08 阶段 6 验收：两个岗位类别配不同的分界线，同样正常率的两个人得到不同的评估结果；没配的类别用默认值。 */
    @Test
    void sameRateIsJudgedByEachJobGroupsOwnThresholds() {
        group("HEAVY", 3, 5, 80, 60, "采煤机司机");
        group("LIGHT", null, null, null, null, "电工");
        person(1, "重一", "采煤机司机", "综采一队");
        person(2, "默一", "信号工", "运输队");
        person(3, "轻一", "电工", "机电队");
        person(4, "重二", "采煤机司机", "综采一队");
        person(5, "默二", null, "运输队");
        person(6, "重三", "采煤机司机", "综采一队");
        person(7, "默三", "信号工", "运输队");
        for (int d = 1; d <= 5; d++) {
            hr(1, aug(d), 100, 85);
            hr(2, aug(d), 100, 85);
            hr(3, aug(d), 100, 85);
            hr(4, aug(d), 100, 65);
            hr(5, aug(d), 100, 65);
        }
        // 4 天：重体力类别最少 3 天，参与评估；默认类别最少 5 天，不参与
        for (int d = 1; d <= 4; d++) {
            hr(6, aug(d), 100, 100);
            hr(7, aug(d), 100, 100);
        }
        // 告警 4 条：重体力类别要 5 条才算风险，默认类别 3 条就算
        for (int k = 0; k < 4; k++) {
            event(6, "HR_HIGH", "HEART_RATE", aug(10 + k).atTime(9, 0));
            event(7, "HR_HIGH", "HEART_RATE", aug(10 + k).atTime(9, 0));
        }

        MonthlyReport r = august();
        Stability hr = r.stability().get("HEART_RATE");

        // 正常率 85%：重体力（≥80）稳定；默认（90 / 70）波动；LIGHT 没配分界线，用默认的，也是波动
        // 正常率 65%：重体力（≥60）波动；默认不稳定
        assertThat(hr.stable()).isEqualTo(2);       // 1、6
        assertThat(hr.fluctuating()).isEqualTo(3);  // 2、3、4
        assertThat(hr.unstable()).isEqualTo(1);     // 5
        assertThat(hr.notEvaluated()).isEqualTo(1); // 7
        assertThat(hr.unstableList()).extracting(UnstablePerson::cardCode).containsExactly(card(5));
        assertThat(hr.unstableList().get(0).groupName()).isEqualTo("默认");
        assertThat(hr.unstableList().get(0).normalPct()).isEqualByComparingTo("65.0");

        assertThat(r.risk().list()).extracting(RiskPerson::cardCode).containsExactly(card(7), card(5));
        assertThat(r.risk().list().get(0).alertCount()).isEqualTo(4);
        assertThat(r.risk().list().get(0).alerts()).extracting(MonthlyReportViews.CodeCount::name)
                .containsExactly("心率偏高");
        assertThat(r.risk().list().get(1).unstable()).containsExactly("HEART_RATE");

        GroupRule light = r.rules().stream().filter(g -> g.code().equals("LIGHT")).findFirst().orElseThrow();
        assertThat(light.minEvalDays()).isEqualTo(5);
        assertThat(light.riskEventCount()).isEqualTo(3);
        assertThat(light.metrics().get("HEART_RATE").stablePct()).isEqualByComparingTo("90");
        assertThat(light.metrics().get("HEART_RATE").unstablePct()).isEqualByComparingTo("70");
        assertThat(r.rules()).extracting(GroupRule::code).containsExactly("DEFAULT", "HEAVY", "LIGHT");
    }

    @Test
    void ratesExactlyOnTheLinesAndDisplayedRoundedDown() {
        person(1, "甲", null, null);
        person(2, "乙", null, null);
        person(3, "丙", null, null);
        person(4, "丁", null, null);
        for (int d = 1; d <= 5; d++) {
            hr(1, aug(d), 2000, 1800);  // 正好 90%
            hr(2, aug(d), 10000, 8999); // 89.99%
            hr(3, aug(d), 2000, 1400);  // 正好 70%
            hr(4, aug(d), 10000, 6999); // 69.99%
        }

        Stability hr = august().stability().get("HEART_RATE");

        assertThat(hr.stable()).isEqualTo(1);
        assertThat(hr.fluctuating()).isEqualTo(2);
        assertThat(hr.unstable()).isEqualTo(1);
        assertThat(hr.unstableList().get(0).normalPct()).as("向下取整，不显示成 70.0").isEqualByComparingTo("69.9");
    }

    // ================= 运动量 =================

    @Test
    void stepsUseDailyAverageOfPeopleWithFiveDaysOrMore() {
        for (int i = 1; i <= 13; i++) {
            person(i, "人" + i, null, null);
        }
        for (int d = 1; d <= 5; d++) {
            for (int i = 1; i <= 12; i++) {
                steps(i, aug(d), 1000L * i + (d == 1 ? 3 : 0)); // 第 1 天多 3 步：日均 1000i + 0.6，四舍五入
            }
        }
        // 第 13 个人只有 4 天，步数再少也不排进"较小"
        for (int d = 1; d <= 4; d++) {
            steps(13, aug(d), 1);
        }
        // 第 12 个人多一天 0 步：天数 6，日均 (60000 + 3) / 6 = 10000.5 → 显示 10001，排在第 10 个人（10000.6）后面
        steps(12, aug(6), 0);

        MonthlyReportViews.Steps s = august().steps();

        assertThat(s.ranked()).isEqualTo(12);
        assertThat(s.excluded()).isEqualTo(1);
        assertThat(s.low()).extracting(StepRank::cardCode).startsWith(card(1), card(2)).doesNotContain(card(13));
        assertThat(s.low().get(0).avgSteps()).isEqualTo(1001);
        assertThat(s.low()).hasSize(10);
        assertThat(s.high()).extracting(StepRank::cardCode).startsWith(card(11), card(10), card(12));
        assertThat(s.high()).extracting(StepRank::avgSteps).startsWith(11001L, 10001L, 10001L);
        assertThat(s.high().get(2).days()).isEqualTo(6);
        assertThat(s.high()).extracting(StepRank::rank).startsWith(1, 2, 3);
    }

    // ================= 缓存 =================

    @Test
    void endedMonthsAreCachedForOneDayCurrentMonthIsNot() {
        person(1, "张三", null, null);
        hr(1, aug(1), 100, 100);
        hr(1, LocalDate.of(2026, 9, 1), 100, 100);
        assertThat(august().watchUsage().userCount()).isEqualTo(1);
        assertThat(service.monthly(YearMonth.of(2026, 9)).watchUsage().userCount()).isEqualTo(1);

        person(2, "李四", null, null);
        hr(2, aug(2), 100, 100);
        hr(2, LocalDate.of(2026, 9, 2), 100, 100);
        assertThat(august().watchUsage().userCount()).as("8 月已结束，用缓存").isEqualTo(1);
        assertThat(august().generatedAt()).isEqualTo(NOW);
        assertThat(service.monthly(YearMonth.of(2026, 9)).watchUsage().userCount()).as("当前月每次重算").isEqualTo(2);

        clock.advance(Duration.ofHours(23));
        assertThat(august().watchUsage().userCount()).isEqualTo(1);
        clock.advance(Duration.ofHours(1));
        assertThat(august().watchUsage().userCount()).as("缓存 1 天后重算").isEqualTo(2);
        assertThat(august().generatedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void lastMonthIsNotCachedUntilItsLastDayIsFinalized() {
        person(1, "张三", null, null);
        hr(1, aug(1), 100, 100);
        clock.set(LocalDateTime.of(2026, 9, 1, 0, 5));
        assertThat(august().watchUsage().userCount()).isEqualTo(1);

        person(2, "李四", null, null);
        hr(2, aug(31), 100, 100);
        assertThat(august().watchUsage().userCount()).as("1 号还不算结束（0:10 才定稿 31 号）").isEqualTo(2);

        clock.set(LocalDateTime.of(2026, 9, 2, 0, 0));
        assertThat(august().watchUsage().userCount()).isEqualTo(2);
        person(3, "王五", null, null);
        hr(3, aug(30), 100, 100);
        assertThat(august().watchUsage().userCount()).as("2 号起缓存").isEqualTo(2);
    }
}
