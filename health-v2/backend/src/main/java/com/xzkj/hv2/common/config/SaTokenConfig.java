package com.xzkj.hv2.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;

/**
 * 除登录和登录页提示外，所有 /api 接口都要登录（docs/01 第五节"安全"）。
 * 令牌放请求头 satoken；未登录抛 NotLoginException，由全局异常处理转成 HTTP 401。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/login-hint");
    }
}
