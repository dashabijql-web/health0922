package com.xzkj.health.dto.sleep;

/**
 * 昨夜睡眠异常人员。
 * level 对应页面样式：danger（睡眠<4小时，严重不足）、warn（4-6小时，偏少）。
 */
public record SleepAlertView(
        String name,
        String level,
        String tag,
        String val,
        String desc
) {
}
