package com.xzkj.hv2.auth;

import java.time.LocalDateTime;

/** SYS_USER 一行（docs/04 第三节）。 */
public record SysUser(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt) {

    public static final int STATUS_ENABLED = 1;

    public boolean enabled() {
        return status != null && status == STATUS_ENABLED;
    }
}
