package com.xzkj.hv2.auth;

/** 按用户名记连续登录失败次数。 */
public interface LoginFailureCounter {

    /** 当前失败次数；没有记录时为 0。 */
    long current(String username);

    /** 失败次数加 1，返回加完后的次数。 */
    long increment(String username);

    /** 登录成功后清零。 */
    void clear(String username);
}
