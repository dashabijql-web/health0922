package com.xzkj.hv2.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 登录页默认账号提示（环境变量 LOGIN_SHOW_DEFAULT_ACCOUNT，docs/01 配置表）。 */
@ConfigurationProperties("hv2.login")
public record LoginProperties(boolean showDefaultAccount, String defaultAccountHint) {
}
