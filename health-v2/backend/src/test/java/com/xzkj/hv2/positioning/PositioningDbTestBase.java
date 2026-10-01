package com.xzkj.hv2.positioning;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.xzkj.hv2.common.config.TimeConfig;
import com.xzkj.hv2.support.MutableClock;
import com.xzkj.hv2.support.OracleTestDatabase;

/**
 * 定位的数据库测试共用：Oracle 测试用户、临时的收件箱/备份/失败目录、可拨动的时钟。
 * 每个测试前清空定位表和临时目录。没配置 ORACLE_TEST_USER 时跳过。
 */
@SpringBootTest
@Import(PositioningDbTestBase.TestClock.class)
abstract class PositioningDbTestBase {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 10, 20, 0);
    static final Path ROOT = createTempRoot();

    // DEVICE 有外键指向 POS_PERSON（手表测试留下的），要先删
    private static final List<String> TABLES = List.of("DEVICE", "POS_INGEST_ERROR", "POS_INGEST_FILE", "POS_AREA",
            "POS_STATION", "POS_PERSON", "POS_PERSON_STATE", "POS_PRESENCE_DAILY", "POS_HEADCOUNT_SERIES");

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW, TimeConfig.ZONE);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", OracleTestDatabase::url);
        r.add("spring.datasource.username", OracleTestDatabase::user);
        r.add("spring.datasource.password", OracleTestDatabase::password);
        r.add("hv2.positioning.scan-enabled", () -> "false");
        r.add("hv2.positioning.inbox-dir", () -> ROOT.resolve("inbox").toString());
        r.add("hv2.positioning.backup-dir", () -> ROOT.resolve("backup").toString());
        r.add("hv2.positioning.error-dir", () -> ROOT.resolve("error").toString());
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MutableClock clock;

    @BeforeAll
    static void database() {
        assumeTrue(OracleTestDatabase.available(), "没有配置 ORACLE_TEST_USER，跳过");
        OracleTestDatabase.migrateOnce();
    }

    @BeforeEach
    void resetTablesAndDirs() throws IOException {
        TABLES.forEach(t -> jdbc.update("DELETE FROM " + t));
        deleteTree(ROOT);
        Files.createDirectories(mineInbox());
        clock.set(NOW);
    }

    static Path mineInbox() {
        return ROOT.resolve("inbox/jxry/" + FakeFiles.MINE);
    }

    int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    private static Path createTempRoot() {
        try {
            return Files.createTempDirectory("hv2-positioning-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
