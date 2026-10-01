package com.xzkj.hv2.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.xzkj.hv2.report.WearExportService;

/**
 * 佩戴情况导出（docs/05 第四节；docs/08 阶段 3 验收"Excel：工作表名、列与 05 一致，上报数据与库里记录一致"）。
 * 读回生成的 .xlsx 逐格检查，再和照 docs/05 口径写的对照 SQL 比。
 */
class WearExportServiceTest extends DashboardDbTestBase {

    private static final LocalDate DAY = TODAY.minusDays(1);

    @Autowired
    private WearExportService service;

    /**
     * 1–5 号那天下过井；6 号只在前一天下过井。1、2、3、6 号绑定着启用的表，4 号的表停用，5 号没表。
     * 1 号那天 23:59:59 有数据（正常）；2 号只有第二天 0 点整的数据（未上传）；3 号没有数据。1 号没有部门。
     */
    @BeforeEach
    void scene() {
        for (int i = 1; i <= 6; i++) {
            person(i, i == 1 ? null : "综采队");
        }
        for (int i = 1; i <= 5; i++) {
            presence(DAY, i);
        }
        presence(DAY.minusDays(1), 6);
        watch(1, card(1), null, 1);
        watch(2, card(2), null, 1);
        watch(3, card(3), null, 1);
        watch(4, card(4), null, 0);
        watch(6, card(6), null, 1);
        record(1, "HEART_RATE", DAY.atTime(23, 59, 59));
        record(2, "SPO2", TODAY.atStartOfDay());
        record(6, "HEART_RATE", DAY.atTime(10, 0));
    }

    @Test
    void workbookMatchesSql() throws IOException {
        List<List<String>> cells = read(service.workbook(DAY));

        assertThat(cells.getFirst()).containsExactly("统计日期", "人员卡编码", "姓名", "部门", "上报数据");
        assertThat(cells.subList(1, cells.size())).containsExactly(
                List.of("2026-09-22", card(2), "测试2", "综采队", "数据未上传"),
                List.of("2026-09-22", card(3), "测试3", "综采队", "数据未上传"),
                List.of("2026-09-22", card(1), "测试1", "未录入", "数据上传正常"));

        // 对照 SQL：那天有入井记录、绑定着启用的手表的人；那天有体征记录算正常
        List<Map<String, Object>> expected = jdbc.queryForList("""
                SELECT pd.CARD_CODE,
                       (SELECT COUNT(*) FROM HEALTH_RECORD r WHERE r.CARD_CODE = pd.CARD_CODE
                           AND r.COLLECTED_AT >= ? AND r.COLLECTED_AT < ?) N
                  FROM POS_PRESENCE_DAILY pd
                 WHERE pd.STAT_DATE = ?
                   AND pd.CARD_CODE IN (SELECT CARD_CODE FROM DEVICE WHERE STATUS = 1)
                 ORDER BY pd.CARD_CODE""",
                ts(DAY.atStartOfDay()), ts(TODAY.atStartOfDay()), Date.valueOf(DAY));
        assertThat(cells).hasSize(expected.size() + 1);
        for (Map<String, Object> e : expected) {
            List<String> row = cells.stream().filter(c -> c.get(1).equals(e.get("CARD_CODE"))).findFirst().orElseThrow();
            boolean uploaded = ((Number) e.get("N")).intValue() > 0;
            assertThat(row.get(4)).isEqualTo(uploaded ? "数据上传正常" : "数据未上传");
        }
    }

    @Test
    void sheetNameAndTextCardCode() throws IOException {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(service.workbook(DAY)))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("数据上报记录");
            assertThat(sheet.getRow(1).getCell(1).getCellType()).as("17 位卡编码按文本存").isEqualTo(CellType.STRING);
        }
    }

    @Test
    void emptyDayHasOnlyHeader() throws IOException {
        assertThat(read(service.workbook(DAY.minusDays(5)))).hasSize(1);
    }

    @Test
    void defaultsAndFileName() {
        assertThat(service.defaultDate()).isEqualTo(DAY);
        assertThat(service.fileName()).isEqualTo("佩戴情况_20260923100000.xlsx");
    }

    private static List<List<String>> read(byte[] xlsx) throws IOException {
        List<List<String>> out = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Row row : wb.getSheet("数据上报记录")) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < 5; c++) {
                    cells.add(row.getCell(c).getStringCellValue());
                }
                out.add(cells);
            }
        }
        return out;
    }
}
