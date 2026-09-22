package com.xzkj.health.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DruidDataSource dataSource() {
        return new DruidDataSource();
    }
}
