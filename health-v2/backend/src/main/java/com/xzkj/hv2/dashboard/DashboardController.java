package com.xzkj.hv2.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xzkj.hv2.alert.AlertLabels;
import com.xzkj.hv2.common.api.ApiResponse;
import com.xzkj.hv2.common.api.PageResult;
import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.dashboard.DashboardViews.AlertPerson;
import com.xzkj.hv2.dashboard.DashboardViews.DeviceEvent;
import com.xzkj.hv2.dashboard.DashboardViews.Headcount;
import com.xzkj.hv2.dashboard.DashboardViews.HeadcountSeries;
import com.xzkj.hv2.dashboard.DashboardViews.InWellPerson;
import com.xzkj.hv2.dashboard.DashboardViews.KeyPerson;
import com.xzkj.hv2.dashboard.DashboardViews.Overview;
import com.xzkj.hv2.dashboard.DashboardViews.StepRank;

/** 动态数据页（大屏）的接口（docs/05 第四节）。 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final Clock clock;

    public DashboardController(DashboardService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /** 累计采集数据、四项体征条数、六类告警今天的人数、今天的设备事件条数。 */
    @GetMapping("/overview")
    public ApiResponse<Overview> overview() {
        return ApiResponse.ok(service.overview());
    }

    /** 点开某个告警格子：今天出过这一类告警的人。 */
    @GetMapping("/alerts")
    public ApiResponse<PageResult<AlertPerson>> alerts(@RequestParam String category,
                                                       @RequestParam(defaultValue = "1") @Min(1) int page,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (!AlertLabels.CATEGORIES.contains(category)) {
            throw BizException.badRequest("告警类别不正确");
        }
        return ApiResponse.ok(service.alertPersons(category, page, size));
    }

    /** 点"设备事件 N"：今天的设备事件，最新的在前。 */
    @GetMapping("/device-events")
    public ApiResponse<PageResult<DeviceEvent>> deviceEvents(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.deviceEvents(page, size));
    }

    @GetMapping("/key-persons")
    public ApiResponse<List<KeyPerson>> keyPersons() {
        return ApiResponse.ok(service.keyPersons());
    }

    /** 上线职工曲线；date 默认今天。 */
    @GetMapping("/headcount-series")
    public ApiResponse<HeadcountSeries> headcountSeries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(service.headcountSeries(date != null ? date : LocalDate.now(clock)));
    }

    @GetMapping("/headcount")
    public ApiResponse<Headcount> headcount() {
        return ApiResponse.ok(service.headcount());
    }

    /** 井下人员表，keyword 按姓名或卡编码（含卡号）查。 */
    @GetMapping("/in-well-persons")
    public ApiResponse<PageResult<InWellPerson>> inWellPersons(
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.inWellPersons(keyword, page, size));
    }

    @GetMapping("/steps-rank")
    public ApiResponse<List<StepRank>> stepsRank(@RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ApiResponse.ok(service.stepsRank(limit));
    }
}
