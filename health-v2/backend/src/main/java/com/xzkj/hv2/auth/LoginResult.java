package com.xzkj.hv2.auth;

/** POST /api/auth/login 的 data：{ token, user }。 */
public record LoginResult(String token, UserInfo user) {

    @Override
    public String toString() {
        return "LoginResult[user=" + user + "]";
    }
}
