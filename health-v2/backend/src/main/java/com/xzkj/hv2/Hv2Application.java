package com.xzkj.hv2;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Hv2Application {

    public static void main(String[] args) {
        // 全系统统一北京时间（docs/01 第五节），JDBC 驱动也按它设置会话时区
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(Hv2Application.class, args);
    }
}
