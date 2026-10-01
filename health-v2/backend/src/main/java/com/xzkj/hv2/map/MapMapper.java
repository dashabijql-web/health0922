package com.xzkj.hv2.map;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 地图模式、基站摆放的 SQL（docs/05 第五节、docs/06 第五节）。 */
@Mapper
public interface MapMapper {

    // ---- 人员 ----

    /**
     * 井下的人和所在基站。
     *
     * @param list    null 全部 / KEY 重点监护 / TODAY 今日关注
     * @param keyword 姓名或卡编码"包含"，null 不限
     */
    List<PersonRow> inWellPersons(@Param("list") String list, @Param("keyword") String keyword,
                                  @Param("today") LocalDate today, @Param("onlineSince") LocalDateTime onlineSince,
                                  @Param("activeSince") LocalDateTime activeSince,
                                  @Param("activeDayStart") LocalDateTime activeDayStart);

    /** 体征卡的人员、位置、手表部分；没有这个人时为 null。 */
    CardRow card(@Param("cardCode") String cardCode);

    List<VitalRow> latestVitals(@Param("cardCode") String cardCode);

    List<AlertRow> recentAlerts(@Param("cardCode") String cardCode, @Param("limit") int limit);

    // ---- 基站 ----

    /** 全部基站，按区域和名称排好。 */
    List<StationRow> stations();

    StationRow station(@Param("stationCode") String stationCode);

    boolean stationExists(@Param("stationCode") String stationCode);

    /** 锁住这一行再读（SELECT … FOR UPDATE），同一基站的改动排队进行；没摆放时为 null。 */
    MarkRow lockMark(@Param("stationCode") String stationCode);

    int insertMark(@Param("stationCode") String stationCode, @Param("x") BigDecimal x, @Param("y") BigDecimal y,
                   @Param("displayName") String displayName, @Param("username") String username,
                   @Param("now") LocalDateTime now);

    /** 版本号对得上才改，改完版本号加 1；返回改了几行。 */
    int updateMark(@Param("stationCode") String stationCode, @Param("x") BigDecimal x, @Param("y") BigDecimal y,
                   @Param("displayName") String displayName, @Param("username") String username,
                   @Param("now") LocalDateTime now, @Param("version") long version);

    int deleteMark(@Param("stationCode") String stationCode, @Param("version") long version);

    /** 最后一个改动这个基站摆放的人（冲突提示"刚刚被 xxx 修改"用）；没有记录时为 null。 */
    String lastStationActor(@Param("stationCode") String stationCode);

    // ---- 操作日志 ----

    int countStationLogs(@Param("stationCode") String stationCode);

    List<LogRow> stationLogs(@Param("stationCode") String stationCode, @Param("offset") int offset,
                             @Param("size") int size);

    // ---- 行 ----

    record PersonRow(String cardCode, String personName, String dept, String areaName, String stationCode,
                     String vendorName, String displayName, String stationAreaName, BigDecimal x, BigDecimal y,
                     LocalDateTime posTime, String watchState, int alerting) {
    }

    record CardRow(String cardCode, String personName, String dept, int inWell, String areaName, String stationCode,
                   String vendorName, String displayName, String stationAreaName, Integer runStatus, BigDecimal x,
                   BigDecimal y, LocalDateTime posTime, LocalDateTime stationEnterTime, String imei,
                   LocalDateTime lastSeenAt, Integer batteryPct, LocalDateTime batteryTime) {
    }

    record VitalRow(String metric, BigDecimal val1, BigDecimal val2, LocalDateTime collectedAt) {
    }

    record AlertRow(String src, String code, String category, String valText, LocalDateTime occurredAt,
                    LocalDateTime lastOccurredAt) {
    }

    record StationRow(String stationCode, String vendorName, String displayName, String areaCode, String areaName,
                      Integer runStatus, BigDecimal x, BigDecimal y, Long version, String updatedBy,
                      LocalDateTime updatedAt) {
    }

    record MarkRow(String stationCode, String displayName, BigDecimal x, BigDecimal y, long version) {
    }

    record LogRow(long id, String username, String action, String targetId, String vendorName, String displayName,
                  String areaName, String beforeJson, String afterJson, LocalDateTime createdAt) {
    }
}
