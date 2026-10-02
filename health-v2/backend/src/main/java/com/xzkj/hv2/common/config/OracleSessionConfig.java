package com.xzkj.hv2.common.config;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariDataSource;

/**
 * 每条数据库连接建好后先执行的语句（docs/01 第五节"配置"、docs/04 第三节）：
 * <ul>
 *   <li>会话时区设为北京时间；</li>
 *   <li>配置了 {@code hv2.db.schema}（环境变量 {@code ORACLE_SCHEMA}）时，再把默认 schema 切到它。
 *       上线时后端用应用账号登录，表属于建表账号 HEALTH_V2；SQL 里的表名不带账号前缀，所以要切过去。
 *       没配置时不切换，和以前一样用登录账号自己的表。</li>
 * </ul>
 */
@Configuration
public class OracleSessionConfig {

    static final String TIME_ZONE_SQL = "ALTER SESSION SET TIME_ZONE = 'Asia/Shanghai'";
    /** Oracle 不带引号的标识符：字母开头，字母、数字、_ $ #，最长 128 */
    private static final Pattern SCHEMA = Pattern.compile("[A-Za-z][A-Za-z0-9_$#]{0,127}");

    @Bean
    static BeanPostProcessor oracleSessionInit(@Value("${hv2.db.schema:}") String schema) {
        String sql = initSql(schema);
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof HikariDataSource ds) {
                    ds.setConnectionInitSql(sql);
                }
                return bean;
            }
        };
    }

    static String initSql(String schema) {
        if (schema == null || schema.isBlank()) {
            return TIME_ZONE_SQL;
        }
        String s = schema.trim();
        if (!SCHEMA.matcher(s).matches()) {
            throw new IllegalStateException("ORACLE_SCHEMA 不是合法的 Oracle 账号名：" + s);
        }
        // Hikari 只执行一条语句，两句 ALTER SESSION 包进一个 PL/SQL 块
        return "BEGIN EXECUTE IMMEDIATE q'[" + TIME_ZONE_SQL + "]'; "
                + "EXECUTE IMMEDIATE 'ALTER SESSION SET CURRENT_SCHEMA = " + s.toUpperCase() + "'; END;";
    }
}
