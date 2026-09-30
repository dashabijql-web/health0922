package com.xzkj.hv2.auth;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xzkj.hv2.common.api.ApiResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录页提示，不需要登录；LOGIN_SHOW_DEFAULT_ACCOUNT=false 时 data 为 null。 */
    @GetMapping("/login-hint")
    public ApiResponse<LoginHint> loginHint() {
        return ApiResponse.ok(authService.loginHint());
    }

    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserInfo> me() {
        return ApiResponse.ok(authService.me());
    }
}
