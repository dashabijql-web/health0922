package com.xzkj.hv2.positioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 用厂家真实样例核对解析和入库的数量（docs/08 阶段 1 验收）。
 * <p>
 * 只在本机有 reference/vendor-samples/ 时运行（该目录含真实姓名，已被 Git 忽略）。
 * 样例只读：直接读取后解析入库到测试用户，不复制、不移动；断言只比较数量，不输出姓名。
 */
class VendorSampleCheckTest extends PositioningDbTestBase {

    private static final Path SAMPLES = Path.of("../reference/vendor-samples");

    @Autowired
    private PositioningIngestService ingest;

    @Test
    void countsMatchTheVendorSamples() throws Exception {
        assumeTrue(Files.isDirectory(SAMPLES), "本机没有厂家样例，跳过");

        // 故意先处理 RYSS（9 月）再处理 RYXX（3 月）：旧的人员信息也要补全从 RYSS 补录的人
        ParsedFile ryss = ingest("RYSS");
        ParsedFile ryxx = ingest("RYXX");
        ingest("RYQY");
        ingest("JZSS");
        ingest("RYJZ");

        assertThat(ryxx.recordCount()).isEqualTo(1517);
        assertThat(ryxx.errorCount()).isZero();
        // 只取原因比较，断言失败时也不会把记录原文（含姓名）打印出来
        assertThat(ryxx.issues()).extracting(RecordIssue::kind).containsExactly(RecordIssue.Kind.WARN);
        assertThat(ryxx.issues()).extracting(RecordIssue::reason).as("一个卡编码出现两次，记一条警告")
                .singleElement().asString().contains("人员卡编码").contains("重复");
        assertThat(count("SELECT COUNT(*) FROM POS_PERSON WHERE FROM_RYXX = 1")).isEqualTo(1516);

        assertThat(ryss.recordCount()).isEqualTo(85);
        assertThat(ryss.errorCount()).isZero();
        assertThat(count("SELECT COUNT(*) FROM POS_PERSON WHERE FROM_RYXX = 0")).as("RYSS 里不在 RYXX 的人")
                .isEqualTo(14);
        assertThat(count("SELECT COUNT(*) FROM POS_PERSON")).isEqualTo(1530);

        assertThat(count("SELECT COUNT(*) FROM POS_AREA")).isEqualTo(31);
        assertThat(count("SELECT COUNT(*) FROM POS_STATION WHERE NAME_SRC_TIME IS NOT NULL")).isEqualTo(130);
        assertThat(count("SELECT COUNT(*) FROM POS_STATION WHERE STATUS_SRC_TIME IS NOT NULL")).isEqualTo(131);
        assertThat(count("SELECT COUNT(*) FROM POS_STATION")).isEqualTo(133);
        assertThat(count("SELECT COUNT(*) FROM POS_STATION WHERE STATION_NAME IS NULL"))
                .as("JZSS 多出 3 个没有名称的").isEqualTo(3);

        assertThat(count("SELECT COUNT(*) FROM V_POS_IN_WELL")).isEqualTo(82);
        assertThat(jdbc.queryForList("SELECT STATUS FROM POS_INGEST_FILE ORDER BY ID", String.class))
                .containsOnly("DONE");

        assertThat(PositioningFileName.parse(sample("RYCS").getFileName().toString()).discarded()).isTrue();
    }

    private ParsedFile ingest(String type) throws IOException, PositioningFormatException {
        Path file = sample(type);
        PositioningFileName name = PositioningFileName.parse(file.getFileName().toString());
        ParsedFile parsed = PositioningParser.parse(name.knownType(), name.mineCode(), Files.readAllBytes(file));
        ingest.ingest(parsed, new PositioningIngestService.FileFacts(name.fileName(), name.mineCode(),
                "sample", Files.size(file), null, null));
        return parsed;
    }

    private static Path sample(String type) throws IOException {
        try (Stream<Path> s = Files.list(SAMPLES)) {
            List<Path> found = s.filter(p -> p.getFileName().toString().contains("_" + type + "_")).toList();
            assertThat(found).as(type + " 样例").hasSize(1);
            return found.get(0);
        }
    }
}
