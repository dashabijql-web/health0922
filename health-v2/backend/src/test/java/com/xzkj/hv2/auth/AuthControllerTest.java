package com.xzkj.hv2.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cn.dev33.satoken.spring.SaBeanInject;
import cn.dev33.satoken.spring.SaBeanRegister;
import cn.dev33.satoken.spring.SaTokenContextRegister;
import com.xzkj.hv2.common.config.SaTokenConfig;
import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.common.exception.GlobalExceptionHandler;

/** 接口层：返回格式、HTTP 状态码、登录拦截（docs/05 第八节，docs/01 第五节）。 */
@WebMvcTest(controllers = AuthController.class)
@ImportAutoConfiguration({SaTokenContextRegister.class, SaBeanRegister.class, SaBeanInject.class})
@org.springframework.context.annotation.Import({SaTokenConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginHintNeedsNoTokenAndReturnsText() throws Exception {
        when(authService.loginHint()).thenReturn(new LoginHint("默认账号 admin，密码 admin"));

        mvc.perform(get("/api/auth/login-hint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.text").value("默认账号 admin，密码 admin"));
    }

    @Test
    void loginHintIsNullWhenSwitchOff() throws Exception {
        when(authService.loginHint()).thenReturn(null);

        mvc.perform(get("/api/auth/login-hint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(authService.login("admin", "admin"))
                .thenReturn(new LoginResult("t-1", new UserInfo(1, "admin", "Admin")));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("t-1"))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(BizException.unauthorized("用户名或密码错误"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void lockedIs429() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new BizException(HttpStatus.TOO_MANY_REQUESTS, "密码错误次数过多，请 10 分钟后再试"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("密码错误次数过多，请 10 分钟后再试"));
    }

    @Test
    void blankUsernameIs400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请输入用户名"));
    }

    @Test
    void malformedBodyIs400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void meWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void meWithUnknownTokenIs401() throws Exception {
        mvc.perform(get("/api/auth/me").header("satoken", "not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void logoutWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownApiPathWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/not-exist"))
                .andExpect(status().isUnauthorized());
    }
}
