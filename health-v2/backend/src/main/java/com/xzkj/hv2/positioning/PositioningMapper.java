package com.xzkj.hv2.positioning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 定位表的读写（docs/02 第四节、docs/04 第四节）。
 * 带 {@code src} 参数的 MERGE 只在文件时间 ≥ 库里这一行依据的时间时才更新，旧文件后到不会覆盖新数据。
 */
@Mapper
public interface PositioningMapper {

    // ---- 处理记录 ----

    /** 同名同摘要、已经入过库（DONE / PARTIAL / STALE）的文件有几份。 */
    int countProcessed(@Param("fileName") String fileName, @Param("sha256") String sha256);

    /** 同类型已生效（DONE / PARTIAL）文件里最新的文件头时间；没有则为 null。 */
    LocalDateTime latestEffectiveHeaderTime(@Param("fileType") String fileType);

    int insertFile(IngestFileEntry entry);

    int insertIssue(@Param("fileId") long fileId, @Param("issue") RecordIssue issue);

    // ---- 各类文件 ----

    int mergeArea(@Param("row") PositioningRow.Area row, @Param("src") LocalDateTime src);

    int mergeStationName(@Param("row") PositioningRow.StationName row, @Param("src") LocalDateTime src);

    int mergeStationStatus(@Param("row") PositioningRow.StationStatus row, @Param("src") LocalDateTime src);

    int mergePerson(@Param("row") PositioningRow.Person row, @Param("src") LocalDateTime src);

    /** RYSS 里出现、人员表里还没有的人：补一行，只有卡编码和姓名。 */
    int insertPersonIfMissing(@Param("cardCode") String cardCode, @Param("personName") String personName,
                              @Param("src") LocalDateTime src);

    int mergePersonState(@Param("row") PositioningRow.PersonState row, @Param("src") LocalDateTime src);

    /** 快照里"已入井"的人：当天一行，取最早入井时刻、最晚快照时间。 */
    int mergePresenceIn(@Param("statDate") LocalDate statDate, @Param("cardCode") String cardCode,
                        @Param("inTime") LocalDateTime inTime, @Param("seenTime") LocalDateTime seenTime);

    /** 快照里"已出井"的人：把出井时刻记到这趟下井涉及的几天（跨零点的夜班两天都有行），取最晚的。 */
    int updatePresenceOut(@Param("cardCode") String cardCode, @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate, @Param("outTime") LocalDateTime outTime);

    int mergeHeadcount(@Param("snapshotTime") LocalDateTime snapshotTime, @Param("inWell") int inWell,
                       @Param("out") int out, @Param("total") int total);

    // ---- 状态接口 ----

    /** 每种文件类型最近处理的一份（按处理顺序），不含文件名不合法的。 */
    List<PositioningStatus.LastFile> latestFilePerType();

    /** 每种文件类型已生效的最新文件头时间。 */
    List<PositioningStatus.TypeTime> latestEffectivePerType();

    int countFailedSince(@Param("since") LocalDateTime since);

    int countInWell();
}
