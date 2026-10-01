package com.xzkj.hv2.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 入口页、动态数据页的查询（docs/05 第三、四节），只读。 */
@Mapper
public interface DashboardMapper {

    // ---- 入口页 ----

    List<VitalStatRow> vitalStats(@Param("statDate") LocalDate statDate);

    /** 今天没有步数记录时返回 null。 */
    StepStatRow stepStats(@Param("statDate") LocalDate statDate);

    WatchListCounts watchListCounts(@Param("today") LocalDate today);

    int countEnabledDevices();

    int countInWell();

    // ---- 展示模式 ----

    List<CounterRow> metricCounters();

    LocalDateTime latestWatchDataTime();

    List<CategoryCount> alertPersonCounts(@Param("dayStart") LocalDateTime dayStart,
                                          @Param("dayEnd") LocalDateTime dayEnd);

    List<AlertPersonRow> alertPersons(@Param("category") String category, @Param("dayStart") LocalDateTime dayStart,
                                      @Param("dayEnd") LocalDateTime dayEnd, @Param("offset") int offset,
                                      @Param("size") int size);

    int countAlertPersons(@Param("category") String category, @Param("dayStart") LocalDateTime dayStart,
                          @Param("dayEnd") LocalDateTime dayEnd);

    int countDeviceEvents(@Param("dayStart") LocalDateTime dayStart, @Param("dayEnd") LocalDateTime dayEnd);

    List<DeviceEventRow> deviceEvents(@Param("dayStart") LocalDateTime dayStart, @Param("dayEnd") LocalDateTime dayEnd,
                                      @Param("offset") int offset, @Param("size") int size);

    List<KeyPersonRow> keyPersons();

    // ---- 底部三块 ----

    List<SeriesPoint> headcountSeries(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    HeadcountRow headcount(@Param("onlineSince") LocalDateTime onlineSince,
                           @Param("activeSince") LocalDateTime activeSince,
                           @Param("activeDayStart") LocalDateTime activeDayStart);

    List<InWellPersonRow> inWellPersons(@Param("keyword") String keyword,
                                        @Param("onlineSince") LocalDateTime onlineSince,
                                        @Param("offset") int offset, @Param("size") int size);

    int countInWellPersons(@Param("keyword") String keyword);

    List<StepRankRow> stepsRank(@Param("statDate") LocalDate statDate, @Param("limit") int limit);

    // ---- 行 ----

    record VitalStatRow(String metric, BigDecimal maxV, BigDecimal minV, BigDecimal avgV, LocalDateTime updatedAt) {
    }

    record StepStatRow(BigDecimal maxV, BigDecimal minV, BigDecimal avgV) {
    }

    record WatchListCounts(int keyCount, int todayCount) {
    }

    record CounterRow(String metric, long totalCount) {
    }

    record CategoryCount(String category, int persons) {
    }

    record AlertPersonRow(String cardCode, String deviceImei, String personName, String dept, int eventCount,
                          int occurTotal, LocalDateTime lastAt, String lastCode, String lastVal, String lastSrc) {
    }

    record DeviceEventRow(long id, String cardCode, String deviceImei, String personName, String code,
                          LocalDateTime occurredAt, LocalDateTime lastOccurredAt, int occurCount) {
    }

    record KeyPersonRow(String cardCode, String personName, String dept) {
    }

    record SeriesPoint(LocalDateTime snapshotTime, int inWellCount) {
    }

    record HeadcountRow(int inWell, int expected, int onlineCount, int onlineAlert) {
    }

    record InWellPersonRow(String cardCode, String personName, String dept, String areaName, String watchState,
                           int stationPlaced) {
    }

    record StepRankRow(String cardCode, String personName, String dept, long steps) {
    }
}
