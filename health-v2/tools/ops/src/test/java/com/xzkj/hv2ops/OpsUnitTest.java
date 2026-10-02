package com.xzkj.hv2ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 不连数据库的部分：清单解析、密码规则、路径换算、环境变量（docs/11）。 */
class OpsUnitTest {

    private static final String IMEI1 = "861265000000001";
    private static final String IMEI2 = "861265000000002";
    private static final String CARD1 = "62082300920390001";
    private static final String CARD2 = "62082300920390002";

    @Test
    void readsUtf8WithChineseHeaderAndIgnoresOtherColumns() {
        String csv = "姓名,IMEI,卡编码,型号,备注\r\n张三,861265000000001,62082300920390001,X1,\r\n"
                + "李四,\"861265000000002\",,,\"只登记, 先不绑\"\r\n\r\n";
        DeviceCsv.Parsed p = DeviceCsv.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(p.charset()).isEqualTo("UTF-8");
        assertThat(p.errors()).isEmpty();
        assertThat(p.rows()).containsExactly(
                new DeviceCsv.Row(2, IMEI1, CARD1, "X1"),
                new DeviceCsv.Row(3, IMEI2, null, null));
    }

    @Test
    void bomIsDetected() {
        byte[] body = ("IMEI,卡编码\n" + IMEI1 + "," + CARD1 + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        assertThat(DeviceCsv.parse(withBom).charset()).isEqualTo("UTF-8（带 BOM）");
    }

    @Test
    void excelGbkCsvIsReadAsGbk() {
        byte[] gbk = ("设备号,人员卡编码,型号\r\n" + IMEI1 + "," + CARD1 + ",手表\r\n").getBytes(Charset.forName("GBK"));
        DeviceCsv.Parsed p = DeviceCsv.parse(gbk);

        assertThat(p.charset()).isEqualTo("GBK");
        assertThat(p.errors()).isEmpty();
        assertThat(p.rows()).containsExactly(new DeviceCsv.Row(2, IMEI1, CARD1, "手表"));
    }

    @Test
    void scientificNotationFromExcelIsExplained() {
        DeviceCsv.Parsed p = DeviceCsv.parse(("IMEI,卡编码\n8.61265E+14," + CARD1 + "\n" + IMEI2 + ",6.20823E+16\n")
                .getBytes(StandardCharsets.UTF_8));

        assertThat(p.rows()).isEmpty();
        assertThat(p.errors()).hasSize(2).allMatch(e -> e.contains("科学计数法"));
        assertThat(p.errors().get(0)).startsWith("第 2 行：IMEI");
        assertThat(p.errors().get(1)).startsWith("第 3 行：卡编码");
    }

    @Test
    void wrongLengthsMissingImeiAndDuplicatesAreErrors() {
        String csv = "IMEI,卡编码\n12345," + CARD1 + "\n," + CARD2 + "\n" + IMEI1 + "," + CARD1 + "\n"
                + IMEI1 + ",\n" + IMEI2 + "," + CARD1 + "\n";
        DeviceCsv.Parsed p = DeviceCsv.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(p.errors()).containsExactly(
                "第 2 行：IMEI 应为 15 位数字，实际是 12345",
                "第 3 行：IMEI 为空",
                "第 5 行：IMEI 861265000000001 和第 4 行重复",
                "第 6 行：卡编码 62082300920390001 和第 4 行重复（一个人只能绑一块表）");
    }

    @Test
    void headerMustHaveImeiAndCard() {
        assertThat(DeviceCsv.parse("姓名,IMEI\n".getBytes(StandardCharsets.UTF_8)).errors())
                .containsExactly("第 1 行（表头）要有 IMEI 和 卡编码 两列");
        assertThat(DeviceCsv.parse(new byte[0]).errors()).containsExactly("文件是空的");
    }

    @Test
    void passwordRules() {
        assertThat(SetPassword.check("admin", "short1")).contains("至少 10 位");
        assertThat(SetPassword.check("admin", "onlyletters")).contains("字母和数字");
        assertThat(SetPassword.check("admin", "1234567890")).contains("字母和数字");
        assertThat(SetPassword.check("admin", "Admin2026xyz")).contains("admin");
        assertThat(SetPassword.check("zhangsan", "zhangsan2026")).contains("登录名");
        assertThat(SetPassword.check("admin", " Mine2026well")).contains("空格");
        assertThat(SetPassword.check("admin", "Mine2026well")).isNull();
    }

    @Test
    void gpkgPathBecomesFileUrl() {
        assertThat(MapPublisher.databaseUrl("D:\\hv2\\map\\mine-map.gpkg")).isEqualTo("file:D:/hv2/map/mine-map.gpkg");
        assertThat(MapPublisher.databaseUrl("/opt/hv2-map/mine-map.gpkg")).isEqualTo("file:/opt/hv2-map/mine-map.gpkg");
        assertThat(MapPublisher.databaseUrl("file:/x.gpkg")).isEqualTo("file:/x.gpkg");
    }

    @Test
    void dbFromEnvPromptsForMissingPasswordAndValidatesSchema() {
        Db db = Db.fromEnv(Map.of("ORACLE_HOST", "db1", "ORACLE_SERVICE", "ORCL", "ORACLE_USER", "HEALTH_V2_APP",
                "ORACLE_SCHEMA", "health_v2"), label -> "typed");
        assertThat(db.url()).isEqualTo("jdbc:oracle:thin:@//db1:1521/ORCL");
        assertThat(db.password()).isEqualTo("typed");
        assertThat(db.schema()).isEqualTo("HEALTH_V2");
        assertThat(db.toString()).doesNotContain("typed");

        assertThatThrownBy(() -> Db.fromEnv(Map.of("ORACLE_USER", "A", "ORACLE_PASSWORD", "p",
                "ORACLE_SCHEMA", "X; DROP"), label -> "")).isInstanceOf(OpsException.class);
        assertThatThrownBy(() -> Db.fromEnv(Map.of(), label -> "")).isInstanceOf(OpsException.class);
    }

    @Test
    void jsonEscaping() {
        assertThat(OpLog.json("a\"b\\c\n")).isEqualTo("\"a\\\"b\\\\c\\u000a\"");
        assertThat(OpLog.json(null)).isEqualTo("null");
    }
}
