package com.xzkj.hv2.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 月度汇总的 SQL（docs/07 第二部分"三、计算口径"）。只读汇总表（HEALTH_DAILY_SUMMARY、STEP_DAILY）、
 * 预警事件和每日出入井，不扫体征流水。日期范围 [from, to)，由调用方按所选月份传进来。
 */
@Mapper
public interface MonthlyReportMapper {

    /**
     * 让本事务里的查询都看同一个时刻的数据（日汇总每 5 分钟重算，一份月报的各页不能对不上）。
     * 必须是事务里的第一条语句。
     */
    void readOnlyTransaction();

    /** 有数据的月份 "yyyy-MM"，从早到晚；最晚到 today 所在的月。 */
    List<String> months(@Param("today") LocalDate today);

    /** 本月入过井的人按工种分的人数；工种未设置的一行 name 为 null。多的在前，同样多按拼音。 */
    List<NameCount> presenceByJobKind(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 本月有体征日汇总的人按部门分的人数；部门未录入的一行 name 为 null。 */
    List<NameCount> watchUsersByDept(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 心率、体温、血氧：月最大、月最小、加权求平均用的 Σ(AVG_V × SAMPLE_COUNT) 和 Σ SAMPLE_COUNT、人数。 */
    List<VitalRow> vitals(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 六类告警各自的人数和事件数（没有的类别不出现）。 */
    List<CategoryRow> alertsByCategory(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 六类告警一共涉及的人数（一个人可能有好几类）。 */
    int alertPersons(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 每人本月心率、体温、血氧的天数、样本数、正常样本数、最小最大值，带姓名、部门和现在所属的岗位类别。 */
    List<PersonMetricRow> personMetrics(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<GroupRow> groups();

    List<RuleRow> rules();

    /** 每人本月有步数记录的天数和总步数。 */
    List<StepRow> steps(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 有卡编码的人本月六类告警按事件代码分的次数，带姓名、部门和现在所属的岗位类别。 */
    List<PersonAlertRow> personAlerts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    record NameCount(String name, int cnt) {
    }

    record VitalRow(String metric, BigDecimal maxV, BigDecimal minV, BigDecimal weightedSum, long samples,
                    int persons) {
    }

    record CategoryRow(String category, int persons, int events) {
    }

    record PersonMetricRow(String cardCode, String metric, int days, long samples, long normals, BigDecimal minV,
                           BigDecimal maxV, String personName, String dept, String groupCode) {
    }

    record GroupRow(String groupCode, String groupName, Integer minEvalDays, Integer riskEventCount) {
    }

    record RuleRow(String groupCode, String metric, BigDecimal lowLimit, BigDecimal highLimit, int enabled,
                   BigDecimal stablePct, BigDecimal unstablePct) {
    }

    record StepRow(String cardCode, int days, long total, String personName, String dept) {
    }

    record PersonAlertRow(String cardCode, String code, int events, String personName, String dept,
                          String groupCode) {
    }
}
