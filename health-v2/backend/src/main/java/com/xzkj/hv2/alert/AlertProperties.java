package com.xzkj.hv2.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 预警的配置（docs/01 第五节配置表，docs/03 第四节）。
 *
 * @param dedupMinutes       体征越界、以及低电等其他设备事件的去重间隔
 * @param deviceDedupMinutes SOS、跌倒的去重间隔（重复包多半是同一次事件）
 * @param activeMinutes      告警"仍在发生"的时长（大屏"已上线-告警"用）
 * @param ruleRefreshSeconds 阈值、岗位类别、人员工种多久重新读一次
 */
@ConfigurationProperties("hv2.alert")
public record AlertProperties(int dedupMinutes, int deviceDedupMinutes, int activeMinutes, int ruleRefreshSeconds) {
}
