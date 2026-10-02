package com.xzkj.hv2.report;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 月度汇总的配置（docs/07 第二部分）。
 *
 * @param stepMinDays 运动量排行只统计有步数记录的天数达到它的人（docs/00 第 12 项：5 天）
 * @param cacheTtl    已结束月份的结果缓存多久（1 天）；当前月不缓存
 */
@ConfigurationProperties("hv2.report")
public record ReportProperties(int stepMinDays, Duration cacheTtl) {
}
