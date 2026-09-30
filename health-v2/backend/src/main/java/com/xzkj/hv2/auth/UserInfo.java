package com.xzkj.hv2.auth;

/** 返回给前端的账号信息（不含密码哈希）。 */
public record UserInfo(long id, String username, String displayName) {

    static UserInfo of(SysUser user) {
        return new UserInfo(user.id(), user.username(), user.displayName());
    }
}
