package com.xzkj.hv2.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 统一的时钟（北京时间，docs/01 第五节）和定时任务开关。
 * 业务代码取"现在"一律通过注入的 {@link Clock}，测试里可以换成固定时间。
 */
@Configuration
@EnableScheduling
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
