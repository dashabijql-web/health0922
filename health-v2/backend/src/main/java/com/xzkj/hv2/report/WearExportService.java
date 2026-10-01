package com.xzkj.hv2.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * 佩戴情况导出（docs/05 第四节）：对比"定位系统说在井下的人"和"手表实际上传了数据的人"。
 * 工作表"数据上报记录"，列：统计日期、人员卡编码（完整 17 位）、姓名、部门、上报数据。
 */
@Service
public class WearExportService {

    public static final String SHEET = "数据上报记录";
    public static final List<String> HEADERS = List.of("统计日期", "人员卡编码", "姓名", "部门", "上报数据");
    public static final String UPLOADED = "数据上传正常";
    public static final String NOT_UPLOADED = "数据未上传";
    private static final String NOT_RECORDED = "未录入";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WearExportMapper mapper;
    private final Clock clock;

    public WearExportService(WearExportMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /** 默认导出昨天。 */
    public LocalDate defaultDate() {
        return LocalDate.now(clock).minusDays(1);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public List<WearExportMapper.WearRow> rows(LocalDate date) {
        return mapper.wearRows(date, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    /** 文件名按导出时刻：佩戴情况_yyyyMMddHHmmss.xlsx。 */
    public String fileName() {
        return "佩戴情况_" + LocalDateTime.now(clock).format(FILE_TIME) + ".xlsx";
    }

    public byte[] workbook(LocalDate date) {
        List<WearExportMapper.WearRow> rows = rows(date);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(SHEET);
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS.get(i));
                c.setCellStyle(head);
            }
            String day = date.toString();
            int r = 1;
            for (WearExportMapper.WearRow row : rows) {
                Row x = sheet.createRow(r++);
                x.createCell(0).setCellValue(day);
                // 卡编码按文本写，17 位数字当成数字会丢精度
                x.createCell(1).setCellValue(row.cardCode());
                x.createCell(2).setCellValue(row.personName() == null ? NOT_RECORDED : row.personName());
                x.createCell(3).setCellValue(row.dept() == null ? NOT_RECORDED : row.dept());
                x.createCell(4).setCellValue(row.uploaded() ? UPLOADED : NOT_UPLOADED);
            }
            int[] widths = {14, 22, 14, 28, 16};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
