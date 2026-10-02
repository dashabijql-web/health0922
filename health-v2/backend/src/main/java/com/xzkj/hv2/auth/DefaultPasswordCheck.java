package com.xzkj.hv2.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时检查开发期默认账号（docs/08 阶段 7、docs/11 安全检查）：
 * admin 还是默认密码 admin 时记一条警告；密码已改、登录页却仍提示默认账号时也记一条。
 * 只记结论，不记密码和哈希。检查失败（如数据库暂时连不上）不影响启动。
 */
@Component
public class DefaultPasswordCheck implements ApplicationRunner {

    static final String DEFAULT_USERNAME = "admin";
    static final String DEFAULT_PASSWORD = "admin";

    private static final Logger log = LoggerFactory.getLogger(DefaultPasswordCheck.class);

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginProperties loginProps;

    public DefaultPasswordCheck(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                                LoginProperties loginProps) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.loginProps = loginProps;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            switch (check()) {
                case DEFAULT_PASSWORD -> log.warn("账号 admin 仍是开发期默认密码，上线前用部署工具修改（docs/11）");
                case HINT_WITHOUT_DEFAULT -> log.warn(
                        "账号 admin 的密码已修改，登录页却仍提示默认账号，请设置 LOGIN_SHOW_DEFAULT_ACCOUNT=false");
                case OK -> { }
            }
        } catch (RuntimeException e) {
            log.warn("检查默认密码时出错，跳过：{}", e.getClass().getSimpleName());
        }
    }

    Result check() {
        SysUser admin = userMapper.findByUsername(DEFAULT_USERNAME);
        boolean isDefault = admin != null && admin.enabled()
                && passwordEncoder.matches(DEFAULT_PASSWORD, admin.passwordHash());
        if (isDefault) {
            return Result.DEFAULT_PASSWORD;
        }
        return loginProps.showDefaultAccount() ? Result.HINT_WITHOUT_DEFAULT : Result.OK;
    }

    enum Result { OK, DEFAULT_PASSWORD, HINT_WITHOUT_DEFAULT }
}
