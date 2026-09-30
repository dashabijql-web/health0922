package com.xzkj.hv2.auth;

import org.springframework.stereotype.Component;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 对 Sa-Token 静态方法的一层薄封装，业务代码通过它登录、退出、取当前用户，单元测试里可以替换。
 */
@Component
public class SessionGateway {

    private static final String SESSION_USERNAME = "username";

    /** 登录并返回令牌。 */
    public String login(long userId, String username) {
        StpUtil.login(userId);
        StpUtil.getSession().set(SESSION_USERNAME, username);
        return StpUtil.getTokenValue();
    }

    public void logout() {
        StpUtil.logout();
    }

    /** 当前登录用户；未登录时抛 NotLoginException（转成 HTTP 401）。 */
    public CurrentUser currentUser() {
        long id = StpUtil.getLoginIdAsLong();
        return new CurrentUser(id, StpUtil.getSession().getString(SESSION_USERNAME));
    }
}
