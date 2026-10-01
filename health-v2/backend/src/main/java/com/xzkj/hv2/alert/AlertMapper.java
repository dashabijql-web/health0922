package com.xzkj.hv2.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 预警表的读写（docs/04 第六节）。 */
@Mapper
public interface AlertMapper {

    List<RuleRow> selectRules();

    List<KindGroupRow> selectKindGroups();

    /** 所有人员的工种（没归类、为空的都按 DEFAULT 判断）。 */
    List<PersonKindRow> selectPersonKinds();

    /** 今天 since 之后仍可能被去重合并的事件（每个人每个代码最新的一条）。 */
    List<ActiveEventRow> selectRecentEvents(@Param("dayStart") LocalDateTime dayStart,
                                            @Param("since") LocalDateTime since);

    int insertEvent(AlertEventEntry entry);

    /** 去重期间再次发生：只更新最近发生时间（取更晚的）和次数。 */
    int touchEvent(@Param("id") long id, @Param("at") LocalDateTime at);

    record RuleRow(String groupCode, String metric, BigDecimal lowLimit, BigDecimal highLimit, int severity,
                   int enabled) {
    }

    record KindGroupRow(String jobKind, String groupCode) {
    }

    record PersonKindRow(String cardCode, String jobKind) {
    }

    record ActiveEventRow(long id, String cardCode, String deviceImei, String code, LocalDateTime occurredAt,
                          LocalDateTime lastOccurredAt) {
    }
}
