package com.xzkj.hv2.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DefaultPasswordCheckTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private final SysUserMapper mapper = mock(SysUserMapper.class);

    private DefaultPasswordCheck.Result check(String password, int status, boolean showHint) {
        when(mapper.findByUsername("admin"))
                .thenReturn(new SysUser(1L, "admin", ENCODER.encode(password), "Admin", status, null, null));
        return new DefaultPasswordCheck(mapper, ENCODER, new LoginProperties(showHint, "提示")).check();
    }

    @Test
    void defaultPasswordIsReportedWhetherOrNotHintIsShown() {
        assertThat(check("admin", 1, true)).isEqualTo(DefaultPasswordCheck.Result.DEFAULT_PASSWORD);
        assertThat(check("admin", 1, false)).isEqualTo(DefaultPasswordCheck.Result.DEFAULT_PASSWORD);
    }

    @Test
    void changedPasswordWithHintStillOnIsReported() {
        assertThat(check("a-strong-one", 1, true)).isEqualTo(DefaultPasswordCheck.Result.HINT_WITHOUT_DEFAULT);
    }

    @Test
    void changedPasswordAndHintOffIsOk() {
        assertThat(check("a-strong-one", 1, false)).isEqualTo(DefaultPasswordCheck.Result.OK);
    }

    @Test
    void disabledAdminCannotLogInSoItIsNotADefaultPassword() {
        assertThat(check("admin", 0, false)).isEqualTo(DefaultPasswordCheck.Result.OK);
    }
}
