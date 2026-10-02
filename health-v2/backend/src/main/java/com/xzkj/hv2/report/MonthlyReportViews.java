package com.xzkj.hv2.report;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 月度汇总接口返回的数据（docs/07 第二部分"四、接口"）。某一块没有数据时为 null，页面显示"暂无数据"。
 * 人员一律带完整 17 位卡编码，页面显示后 5 位。
 */
public final class MonthlyReportViews {

    private MonthlyReportViews() {
    }

    /**
     * @param months       有数据的月份，从早到晚，如 "2026-08"
     * @param defaultMonth 默认打开哪个月：上个月有数据就是上个月，否则最近有数据的月份；一个都没有为 null
     */
    public record Months(List<String> months, String defaultMonth) {
    }

    /**
     * 一份月报。
     *
     * @param generatedAt 这份数据算出来的时间（已结束的月份会缓存，见 {@link MonthlyReportService}）
     * @param vitals      HEART_RATE、TEMPERATURE、SPO2 三项都在，某项没有数据为 null；三项都没有时整块为 null
     * @param rules       第 7 页的评估规则：各岗位类别实际用的参数（配置数据，任何月份都有）
     * @param stability   同 vitals：三项都在，某项没人有数据为 null；都没有时整块为 null
     */
    public record MonthlyReport(String month, LocalDateTime generatedAt, Overview overview, WatchUsage watchUsage,
                                Map<String, VitalStat> vitals, Alerts alerts, List<GroupRule> rules,
                                Map<String, Stability> stability, Steps steps, Risk risk) {
    }

    /**
     * 饼图和表格的一行。
     *
     * @param name    工种或部门；kind 是 OTHER 时为"其他"，UNRECORDED 时为 null（页面显示"未录入"）
     * @param kind    NAMED 有名字的一类 / OTHER 前 8 名以外合并 / UNRECORDED 厂家没填
     * @param percent 占总人数的百分比，两位小数
     */
    public record Share(String name, String kind, int count, BigDecimal percent) {
    }

    /**
     * 第 3 页概述。
     *
     * @param workerCount  本月至少有一天入井的不同人数
     * @param jobKindCount 这些人里不同工种的个数（未设置的不算）
     */
    public record Overview(int workerCount, int jobKindCount, List<Share> byJobKind) {
    }

    /**
     * 第 4 页手表使用。
     *
     * @param userCount 本月有体征日汇总的不同人数
     * @param deptCount 这些人所属的不同部门数（未录入的不算）
     */
    public record WatchUsage(int userCount, int deptCount, List<Share> byDept) {
    }

    /**
     * 第 5 页一项体征。
     *
     * @param avg     按每天样本数加权的平均，一位小数
     * @param samples 本月样本总数
     * @param persons 有这项数据的人数
     */
    public record VitalStat(BigDecimal max, BigDecimal min, BigDecimal avg, long samples, int persons) {
    }

    /**
     * 第 6 页六类告警。
     *
     * @param persons    涉及的不同人数（没绑定的手表按设备号算一个人）
     * @param events     事件总数
     * @param byCategory 六类都在，顺序同大屏格子，没有的为 0
     */
    public record Alerts(int persons, int events, List<CategoryCount> byCategory) {
    }

    public record CategoryCount(String category, int persons, int events) {
    }

    /**
     * 一个岗位类别的评估参数（已按"没配就用 DEFAULT 的"补齐）。
     *
     * @param minEvalDays    某指标有数据的天数少于它，不参与稳定性评估
     * @param riskEventCount 六类告警事件达到它，列为风险职工
     * @param metrics        HEART_RATE、TEMPERATURE、SPO2 的正常范围和分界线
     */
    public record GroupRule(String code, String name, int minEvalDays, int riskEventCount,
                            Map<String, MetricRule> metrics) {
    }

    /**
     * @param low         正常范围下限，null 不限
     * @param high        正常范围上限，null 不限
     * @param enabled     阈值停用时全部算正常
     * @param stablePct   正常率达到它算稳定
     * @param unstablePct 正常率低于它算不稳定
     */
    public record MetricRule(BigDecimal low, BigDecimal high, boolean enabled, BigDecimal stablePct,
                             BigDecimal unstablePct) {
    }

    /**
     * 第 8–10 页一项指标的稳定性评估。
     *
     * @param persons      本月有这项数据的人数 = stable + fluctuating + unstable + notEvaluated
     * @param notEvaluated 有数据的天数少于所属岗位类别的最少天数，不参与评估
     * @param unstableList "不稳定"里正常率最低的前 10 人
     */
    public record Stability(int persons, int stable, int fluctuating, int unstable, int notEvaluated,
                            List<UnstablePerson> unstableList) {
    }

    /**
     * @param normalPct 正常率（%），一位小数，向下取整（显示的数和判定一致：89.96 显示 89.9，不会显示成 90.0）
     * @param min       本月最小值
     * @param max       本月最大值
     * @param days      本月有这项数据的天数
     */
    public record UnstablePerson(String cardCode, String name, String dept, String groupName, BigDecimal normalPct,
                                 BigDecimal min, BigDecimal max, int days) {
    }

    /**
     * 第 11、12 页运动量排行。
     *
     * @param minDays  有步数记录的天数达到它才参与排行
     * @param ranked   参与排行的人数
     * @param excluded 有步数记录、但天数不够没参与排行的人数
     * @param low      日均步数最少的前 10 人（从少到多）
     * @param high     日均步数最多的前 10 人（从多到少）；两页的排行条都以 high 第一名为满格
     */
    public record Steps(int minDays, int ranked, int excluded, List<StepRank> low, List<StepRank> high) {
    }

    /**
     * @param avgSteps 日均步数 = 本月总步数 ÷ 有记录的天数，四舍五入到整数
     */
    public record StepRank(int rank, String cardCode, String name, String dept, long avgSteps, int days) {
    }

    /**
     * 第 13 页风险职工。
     *
     * @param total 符合条件的总人数
     * @param list  前 12 人：告警次数多的在前，再按"不稳定"项数
     */
    public record Risk(int total, List<RiskPerson> list) {
    }

    /**
     * @param alertCount 本月六类告警事件数（不含低电、脱落等设备事件）
     * @param unstable   被评为"不稳定"的指标，按心率、体温、血氧的顺序
     * @param alerts     六类告警按事件代码分的次数，多的在前
     */
    public record RiskPerson(String cardCode, String name, String dept, String groupName, int alertCount,
                             List<String> unstable, List<CodeCount> alerts) {
    }

    public record CodeCount(String code, String name, int count) {
    }
}
