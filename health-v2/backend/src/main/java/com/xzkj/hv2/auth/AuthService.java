package com.xzkj.hv2.auth;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.xzkj.hv2.common.exception.BizException;

/**
 * 登录、退出、当前用户（docs/05 第八节）。
 * 用户名不存在也照样计失败次数，提示语和密码错误一样，不让人借此猜出哪些用户名存在。
 */
@Service
public class AuthService {

    static final String MSG_BAD_CREDENTIALS = "用户名或密码错误";
    static final String MSG_LOCKED = "密码错误次数过多，请 10 分钟后再试";
    static final String MSG_DISABLED = "账号已停用";

    private final SysUserMapper userMapper;
    private final LoginFailureCounter failureCounter;
    private final PasswordEncoder passwordEncoder;
    private final SessionGateway session;
    private final AuthProperties authProps;
    private final LoginProperties loginProps;
    /** 用户名不存在时也比对一次，让响应时间和密码错误差不多。 */
    private final String dummyHash;

    public AuthService(SysUserMapper userMapper, LoginFailureCounter failureCounter,
                       PasswordEncoder passwordEncoder, SessionGateway session,
                       AuthProperties authProps, LoginProperties loginProps) {
        this.userMapper = userMapper;
        this.failureCounter = failureCounter;
        this.passwordEncoder = passwordEncoder;
        this.session = session;
        this.authProps = authProps;
        this.loginProps = loginProps;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginHint loginHint() {
        return loginProps.showDefaultAccount() ? new LoginHint(loginProps.defaultAccountHint()) : null;
    }

    public LoginResult login(String rawUsername, String password) {
        String username = rawUsername.trim();
        // 锁定期间密码对了也不让登录，也不再加次数（否则会不断延长锁定）
        if (failureCounter.current(username) >= authProps.maxFailures()) {
            throw locked();
        }

        SysUser user = userMapper.findByUsername(username);
        boolean matched = passwordEncoder.matches(password, user != null ? user.passwordHash() : dummyHash)
                && user != null;
        if (!matched) {
            long failures = failureCounter.increment(username);
            if (failures >= authProps.maxFailures()) {
                throw locked();
            }
            throw BizException.unauthorized(MSG_BAD_CREDENTIALS);
        }
        if (!user.enabled()) {
            throw BizException.forbidden(MSG_DISABLED);
        }

        failureCounter.clear(username);
        String token = session.login(user.id(), user.username());
        userMapper.updateLastLoginAt(user.id());
        return new LoginResult(token, UserInfo.of(user));
    }

    public void logout() {
        session.logout();
    }

    public UserInfo me() {
        CurrentUser current = session.currentUser();
        SysUser user = userMapper.findById(current.id());
        if (user == null || !user.enabled()) {
            session.logout();
            throw BizException.unauthorized("账号不存在或已停用，请重新登录");
        }
        return UserInfo.of(user);
    }

    private static BizException locked() {
        return new BizException(HttpStatus.TOO_MANY_REQUESTS, MSG_LOCKED);
    }
}
