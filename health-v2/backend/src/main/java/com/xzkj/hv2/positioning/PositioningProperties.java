package com.xzkj.hv2.positioning;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定位文件接入的配置（docs/01 第五节配置表，docs/02）。
 *
 * @param inboxDir               收件箱；为空时用 health-v2/runtime/inbox（见 {@link PositioningPaths}）
 * @param backupDir              备份目录；为空时用 health-v2/runtime/backup
 * @param errorDir               失败目录；为空时用 health-v2/runtime/error
 * @param mineCode               本矿煤矿编码，只接收这个矿的文件
 * @param scanEnabled            是否定时扫描收件箱（测试时关掉）
 * @param scanSeconds            扫描间隔（秒）
 * @param staleMinutes           最新 RYSS 距现在超过它，算"定位数据已过期"
 * @param futureToleranceMinutes 文件头时间允许比现在晚多少分钟
 * @param incompleteTimeout      文件一直不完整（末尾没有 ||）超过它，移到失败目录
 * @param maxFileBytes           超过它的文件不解析，移到失败目录
 * @param maxErrorRatio          出错记录超过这个比例，整个文件回滚
 */
@ConfigurationProperties("hv2.positioning")
public record PositioningProperties(
        String inboxDir,
        String backupDir,
        String errorDir,
        String mineCode,
        boolean scanEnabled,
        int scanSeconds,
        int staleMinutes,
        int futureToleranceMinutes,
        Duration incompleteTimeout,
        long maxFileBytes,
        double maxErrorRatio) {
}
