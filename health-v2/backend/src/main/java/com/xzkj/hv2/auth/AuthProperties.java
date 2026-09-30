package com.xzkj.hv2.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 登录锁定参数（docs/05 第八节"登录锁定"）。 */
@ConfigurationProperties("hv2.auth")
public record AuthProperties(int maxFailures, Duration lockDuration, String failKeyPrefix) {
}
