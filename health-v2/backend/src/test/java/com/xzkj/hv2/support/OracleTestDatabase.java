package com.xzkj.hv2.support;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.flywaydb.core.Flyway;

/**
 * 集成测试用的 Oracle：专用测试用户（ORACLE_TEST_USER，必须以 _TEST 结尾），不碰业务用户 HEALTH_V2。
 * <p>
 * 连接信息先看环境变量，再看 health-v2/.env.local；没配置时 {@link #available()} 为 false，测试跳过。
 * 第一次使用时清空这个用户下的全部对象，再用 Flyway 按迁移脚本重建，保证和正式库结构一致。
 */
public final class OracleTestDatabase {

    private static final Properties ENV = loadEnv();
    private static boolean migrated;

    private OracleTestDatabase() {
    }

    public static boolean available() {
        return user() != null && password() != null;
    }

    public static String url() {
        return "jdbc:oracle:thin:@//" + get("ORACLE_HOST", "127.0.0.1") + ":" + get("ORACLE_PORT", "1521")
                + "/" + get("ORACLE_SERVICE", "FREEPDB1");
    }

    public static String user() {
        return get("ORACLE_TEST_USER", null);
    }

    public static String password() {
        return get("ORACLE_TEST_PASSWORD", null);
    }

    /** 清空测试用户并执行全部迁移脚本；同一个 JVM 里只做一次。 */
    public static synchronized void migrateOnce() {
        if (migrated) {
            return;
        }
        if (!user().toUpperCase().endsWith("_TEST")) {
            throw new IllegalStateException("ORACLE_TEST_USER 必须以 _TEST 结尾，拒绝清空 " + user());
        }
        Flyway flyway = Flyway.configure()
                .dataSource(url(), user(), password())
                .locations("filesystem:src/main/resources/db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        migrated = true;
    }

    private static String get(String key, String defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            v = ENV.getProperty(key);
        }
        return v == null || v.isBlank() ? defaultValue : v.trim();
    }

    private static Properties loadEnv() {
        Properties p = new Properties();
        for (Path candidate : new Path[] {Path.of("../.env.local"), Path.of(".env.local")}) {
            if (Files.isRegularFile(candidate)) {
                try (Reader r = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                    p.load(r);
                } catch (IOException e) {
                    throw new IllegalStateException("读取 .env.local 失败", e);
                }
                break;
            }
        }
        return p;
    }
}
