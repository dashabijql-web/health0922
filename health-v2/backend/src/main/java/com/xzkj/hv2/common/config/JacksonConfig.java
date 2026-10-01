package com.xzkj.hv2.common.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 接口里的时间统一写成 "2026-09-24 10:13:19"、日期写成 "2026-09-24"（docs/05 的例子），都是北京时间，不带时区。
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE = "yyyy-MM-dd";

    @Bean
    JsonMapperBuilderCustomizer timeFormat() {
        return builder -> builder
                .withConfigOverride(LocalDateTime.class, o -> o.setFormat(JsonFormat.Value.forPattern(DATE_TIME)))
                .withConfigOverride(LocalDate.class, o -> o.setFormat(JsonFormat.Value.forPattern(DATE)));
    }
}
