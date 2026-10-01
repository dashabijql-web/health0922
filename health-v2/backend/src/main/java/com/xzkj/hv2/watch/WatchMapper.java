package com.xzkj.hv2.watch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 手表链路的查询。成批写库在 {@link com.xzkj.hv2.watch.buffer.WatchBatchWriter} 里用 JDBC 批量执行。
 */
@Mapper
public interface WatchMapper {

    /** 启用的手表及其绑定、最后在线时间。 */
    List<DeviceRow> selectEnabledDevices();

    /** 这个人最近一天的步数记录（不晚于 statDate），用作步数计算的起点；没有则为 null。 */
    StepSeed selectStepSeed(@Param("cardCode") String cardCode, @Param("statDate") LocalDate statDate);

    record DeviceRow(String imei, String cardCode, LocalDateTime lastSeenAt) {
    }

    record StepSeed(LocalDate statDate, long steps, long lastRaw, LocalDateTime updatedAt, String deviceImei) {
    }
}
