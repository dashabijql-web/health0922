package com.xzkj.hv2.archive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 健康档案、个人档案、名单的 SQL（docs/05 第六～八节）。"现在""今天"由调用方按注入的 Clock 传进来。 */
@Mapper
public interface ArchiveMapper {

    // ---- 健康档案列表 ----

    /** 绑定了（启用的）手表的人里出现过的部门，按拼音排。 */
    List<String> depts();

    List<String> jobKinds();

    /**
     * @param dept    部门，null 不限
     * @param jobKind 工种，null 不限
     * @param keyword 姓名或卡编码"包含"，null 不限
     */
    int countPersons(@Param("dept") String dept, @Param("jobKind") String jobKind, @Param("keyword") String keyword);

    List<PersonRow> persons(@Param("dept") String dept, @Param("jobKind") String jobKind,
                            @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    // ---- 个人档案 ----

    /** 人员、启用的手表、当前位置（只有在井下才有）；没有这个人时为 null。 */
    DetailRow detail(@Param("cardCode") String cardCode);

    List<VitalRow> latestVitals(@Param("cardCode") String cardCode);

    StepRow todaySteps(@Param("cardCode") String cardCode, @Param("today") LocalDate today);

    /** 这个人现在在哪些名单里（今日关注只算 today 还有效的）。 */
    List<ListRow> listsOf(@Param("cardCode") String cardCode, @Param("today") LocalDate today);

    List<PointRow> trend(@Param("cardCode") String cardCode, @Param("metric") String metric,
                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<StepDayRow> steps(@Param("cardCode") String cardCode, @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    int countAlerts(@Param("cardCode") String cardCode);

    List<AlertRow> alerts(@Param("cardCode") String cardCode, @Param("offset") int offset, @Param("size") int size);

    boolean personExists(@Param("cardCode") String cardCode);

    // ---- 年龄 ----

    /** 锁住这个人再读年龄（SELECT … FOR UPDATE），两人同时录入时排队；没有这个人时为 null。 */
    AgeRow lockAge(@Param("cardCode") String cardCode);

    int updateAge(@Param("cardCode") String cardCode, @Param("age") int age, @Param("username") String username,
                  @Param("now") LocalDateTime now);

    // ---- 名单 ----

    /** 某类名单里还有效的人，最新加入的在前。 */
    List<ListItemRow> watchList(@Param("type") String type, @Param("today") LocalDate today);

    ListItemRow watchListItem(@Param("id") long id);

    /** 锁住（卡编码, 类型）这一行再读；没有时为 null。 */
    ListItemRow lockWatchListItem(@Param("cardCode") String cardCode, @Param("type") String type);

    /** 按（卡编码, 类型）MERGE：已有（包括过期的今日关注）就覆盖备注、有效期、加入人和时间。 */
    int mergeWatchList(@Param("cardCode") String cardCode, @Param("type") String type, @Param("note") String note,
                       @Param("expireDate") LocalDate expireDate, @Param("username") String username,
                       @Param("now") LocalDateTime now);

    ListItemRow lockWatchListById(@Param("id") long id);

    int deleteWatchList(@Param("id") long id);

    // ---- 行 ----

    record PersonRow(String cardCode, String personName, String jobKind, String dept, Integer age) {
    }

    record DetailRow(String cardCode, String personName, String jobKind, String dept, Integer age,
                     String ageUpdatedBy, LocalDateTime ageUpdatedAt, int inWell, String areaName,
                     String stationCode, String vendorName, String displayName, String stationAreaName,
                     Integer runStatus, BigDecimal x, LocalDateTime posTime, String imei, LocalDateTime lastSeenAt,
                     Integer batteryPct, LocalDateTime batteryTime) {
    }

    record VitalRow(String metric, BigDecimal val1, BigDecimal val2, LocalDateTime collectedAt) {
    }

    record StepRow(long steps, LocalDateTime updatedAt) {
    }

    record ListRow(long id, String listType, String note, LocalDate expireDate, String addedBy,
                   LocalDateTime addedAt) {
    }

    record PointRow(LocalDateTime collectedAt, BigDecimal val1, BigDecimal val2) {
    }

    record StepDayRow(LocalDate statDate, long steps) {
    }

    record AlertRow(long id, String src, String code, String category, String valText, LocalDateTime occurredAt,
                    LocalDateTime lastOccurredAt, int occurCount) {
    }

    record AgeRow(String cardCode, Integer age) {
    }

    record ListItemRow(long id, String cardCode, String personName, String dept, String listType, String note,
                       LocalDate expireDate, String addedBy, LocalDateTime addedAt) {
    }
}
