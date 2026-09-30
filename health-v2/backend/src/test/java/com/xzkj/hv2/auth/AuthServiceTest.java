package com.xzkj.hv2.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.xzkj.hv2.common.exception.BizException;

class AuthServiceTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String ADMIN_HASH = ENCODER.encode("admin");

    private SysUserMapper userMapper;
    private SessionGateway session;
    private InMemoryFailureCounter counter;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        session = mock(SessionGateway.class);
        counter = new InMemoryFailureCounter();
        when(userMapper.findByUsername("admin")).thenReturn(user(1));
        when(session.login(anyLong(), anyString())).thenReturn("token-abc");
        service = newService(true);
    }

    private AuthService newService(boolean showHint) {
        return new AuthService(userMapper, counter, ENCODER, session,
                new AuthProperties(5, Duration.ofMinutes(10), "hv2:auth:fail:"),
                new LoginProperties(showHint, "默认账号 admin，密码 admin"));
    }

    private static SysUser user(int status) {
        return new SysUser(1L, "admin", ADMIN_HASH, "Admin", status, null, null);
    }

    @Test
    void loginSuccessReturnsTokenAndClearsFailures() {
        counter.counts.put("admin", 3L);

        LoginResult result = service.login(" admin ", "admin");

        assertThat(result.token()).isEqualTo("token-abc");
        assertThat(result.user().username()).isEqualTo("admin");
        assertThat(counter.current("admin")).isZero();
        verify(userMapper).updateLastLoginAt(1L);
    }

    @Test
    void wrongPasswordCountsAndReturns401() {
        assertThatThrownBy(() -> service.login("admin", "wrong"))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).isEqualTo(AuthService.MSG_BAD_CREDENTIALS);
                });
        assertThat(counter.current("admin")).isEqualTo(1);
    }

    @Test
    void unknownUserCountsWithSameMessage() {
        assertThatThrownBy(() -> service.login("nobody", "x"))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).isEqualTo(AuthService.MSG_BAD_CREDENTIALS);
                });
        assertThat(counter.current("nobody")).isEqualTo(1);
    }

    @Test
    void fifthFailureLocksAccount() {
        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> service.login("admin", "wrong"))
                    .hasMessage(AuthService.MSG_BAD_CREDENTIALS);
        }
        assertThatThrownBy(() -> service.login("admin", "wrong"))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getMessage()).isEqualTo(AuthService.MSG_LOCKED);
                });
        assertThat(counter.current("admin")).isEqualTo(5);
    }

    @Test
    void lockedAccountRejectsCorrectPasswordWithoutExtendingLock() {
        counter.counts.put("admin", 5L);

        assertThatThrownBy(() -> service.login("admin", "admin"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(counter.current("admin")).isEqualTo(5);
        verify(session, never()).login(anyLong(), anyString());
    }

    @Test
    void lockExpiredAllowsLoginAgain() {
        counter.counts.put("admin", 5L);
        counter.clear("admin"); // Redis 里的 key 到期消失

        assertThat(service.login("admin", "admin").token()).isEqualTo("token-abc");
    }

    @Test
    void disabledUserWithCorrectPasswordIsForbidden() {
        when(userMapper.findByUsername("admin")).thenReturn(user(0));

        assertThatThrownBy(() -> service.login("admin", "admin"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(session, never()).login(anyLong(), anyString());
    }

    @Test
    void loginHintFollowsSwitch() {
        assertThat(service.loginHint().text()).isEqualTo("默认账号 admin，密码 admin");
        assertThat(newService(false).loginHint()).isNull();
    }
}
