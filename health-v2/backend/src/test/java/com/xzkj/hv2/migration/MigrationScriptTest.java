package com.xzkj.hv2.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 迁移脚本的静态检查（不连数据库）。 */
class MigrationScriptTest {

    private static final Path DIR = Path.of("src/main/resources/db/migration");

    private static List<Path> scripts() throws IOException {
        try (Stream<Path> files = Files.list(DIR)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".sql")).sorted().toList();
        }
    }

    @Test
    void namesFollowFlywayConvention() throws IOException {
        assertThat(scripts()).extracting(p -> p.getFileName().toString())
                .allMatch(n -> n.matches("V\\d{3}__[a-z0-9_]+\\.sql"))
                .startsWith("V001__sys.sql", "V002__seed_admin.sql");
    }

    @Test
    void everyVarchar2UsesCharSemantics() throws IOException {
        // docs/04：字符串一律 VARCHAR2(n CHAR)，按字符计长度，中文不会被截断
        Pattern byteSemantics = Pattern.compile("VARCHAR2\\s*\\(\\s*\\d+\\s*(BYTE\\s*)?\\)", Pattern.CASE_INSENSITIVE);
        for (Path script : scripts()) {
            String sql = Files.readString(script, StandardCharsets.UTF_8);
            assertThat(byteSemantics.matcher(sql).find())
                    .as("%s 里有不带 CHAR 的 VARCHAR2", script.getFileName())
                    .isFalse();
        }
    }

    @Test
    void seedAdminStoresBcryptOfDefaultPassword() throws IOException {
        String sql = Files.readString(DIR.resolve("V002__seed_admin.sql"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("'(\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53})'").matcher(sql);
        assertThat(m.find()).as("V002 里应有 BCrypt 哈希").isTrue();
        assertThat(new BCryptPasswordEncoder().matches("admin", m.group(1))).isTrue();
        assertThat(sql).doesNotContain("'admin', 'admin'");
    }

    @Test
    void operationLogIsGuardedByTrigger() throws IOException {
        String sql = Files.readString(DIR.resolve("V001__sys.sql"), StandardCharsets.UTF_8);
        assertThat(sql).contains("BEFORE UPDATE OR DELETE ON SYS_OPERATION_LOG")
                .contains("RAISE_APPLICATION_ERROR(-20001");
    }
}
