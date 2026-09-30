package com.xzkj.hv2.auth;

/** 当前登录的人：账号 ID 和登录名。 */
public record CurrentUser(long id, String username) {
}
