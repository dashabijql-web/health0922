package com.xzkj.hv2.report;

import java.time.YearMonth;

import jakarta.validation.constraints.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xzkj.hv2.common.api.ApiResponse;
import com.xzkj.hv2.report.MonthlyReportViews.Months;
import com.xzkj.hv2.report.MonthlyReportViews.MonthlyReport;

/** 月度汇总的接口（docs/07 第二部分"四、接口"）。月份写成 yyyy-MM，格式不对或晚于本月返回 400。 */
@RestController
@RequestMapping("/api/report")
public class MonthlyReportController {

    private final MonthlyReportService service;

    public MonthlyReportController(MonthlyReportService service) {
        this.service = service;
    }

    @GetMapping("/months")
    public ApiResponse<Months> months() {
        return ApiResponse.ok(service.months());
    }

    @GetMapping("/monthly")
    public ApiResponse<MonthlyReport> monthly(
            @RequestParam @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "月份格式应为 yyyy-MM") String month) {
        return ApiResponse.ok(service.monthly(YearMonth.parse(month)));
    }
}
