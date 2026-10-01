package com.xzkj.hv2.report;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xzkj.hv2.common.exception.BizException;

/** 佩戴情况导出：GET /api/export/wear?date=2026-09-23，默认昨天（docs/05 第四节）。 */
@RestController
@RequestMapping("/api/export")
public class WearExportController {

    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final WearExportService service;

    public WearExportController(WearExportService service) {
        this.service = service;
    }

    @GetMapping("/wear")
    public ResponseEntity<byte[]> wear(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : service.defaultDate();
        if (day.isAfter(service.today())) {
            throw BizException.badRequest("不能导出还没到的日期");
        }
        byte[] body = service.workbook(day);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(service.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(body);
    }
}
