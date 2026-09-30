package com.xzkj.hv2.positioning;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/positioning/status 的返回（docs/02 第五节，docs/05 运维接口）。
 *
 * @param now           服务器当前时间
 * @param staleMinutes  过期阈值（分钟）
 * @param stale         最新一份生效的 RYSS 距现在超过阈值，或者从没收到过 RYSS
 * @param lastDataTime  最新一份生效的 RYSS 的数据上传时间；没有则为 null
 * @param inWellCount   按最新一份 RYSS 算的井下人数（V_POS_IN_WELL）；没有 RYSS 时为 null
 * @param failedLast24h 最近 24 小时失败的文件数
 * @param types         每种文件类型的情况，五类必有，另有收到过的未识别类型
 */
public record PositioningStatus(LocalDateTime now, int staleMinutes, boolean stale, LocalDateTime lastDataTime,
                                Integer inWellCount, int failedLast24h, List<TypeStatus> types) {

    /**
     * @param effectiveDataTime 已生效（DONE / PARTIAL）文件里最新的数据上传时间
     * @param lastFile          最近处理的一份文件（不论成败），没有则为 null
     */
    public record TypeStatus(String fileType, LocalDateTime effectiveDataTime, LastFile lastFile) {
    }

    public record LastFile(String fileType, String fileName, String status, LocalDateTime headerTime,
                           LocalDateTime processedAt, Integer recordCount, Integer errorCount, String errorMsg) {
    }

    public record TypeTime(String fileType, LocalDateTime headerTime) {
    }
}
