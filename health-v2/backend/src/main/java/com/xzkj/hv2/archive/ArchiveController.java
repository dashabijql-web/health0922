package com.xzkj.hv2.archive;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xzkj.hv2.archive.ArchiveViews.AgeResult;
import com.xzkj.hv2.archive.ArchiveViews.AlertRecord;
import com.xzkj.hv2.archive.ArchiveViews.ArchivePerson;
import com.xzkj.hv2.archive.ArchiveViews.Filters;
import com.xzkj.hv2.archive.ArchiveViews.PersonDetail;
import com.xzkj.hv2.archive.ArchiveViews.StepDays;
import com.xzkj.hv2.archive.ArchiveViews.Trend;
import com.xzkj.hv2.common.api.ApiResponse;
import com.xzkj.hv2.common.api.PageResult;
import com.xzkj.hv2.common.exception.BizException;

/** 健康档案、个人档案的接口（docs/05 第六、七节）。卡编码 17 位字母数字，否则 400；没有这个人 404。 */
@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    static final String CARD = "^[0-9A-Za-z]{17}$";

    private final ArchiveService service;

    public ArchiveController(ArchiveService service) {
        this.service = service;
    }

    @GetMapping("/filters")
    public ApiResponse<Filters> filters() {
        return ApiResponse.ok(service.filters());
    }

    /** 绑定了手表的人，按姓名拼音排；dept、jobKind 精确匹配，keyword 按姓名或卡编码"包含"查。 */
    @GetMapping("/persons")
    public ApiResponse<PageResult<ArchivePerson>> persons(
            @RequestParam(required = false) @Size(max = 100) String dept,
            @RequestParam(required = false) @Size(max = 50) String jobKind,
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(service.persons(dept, jobKind, keyword, page, size));
    }

    @GetMapping("/persons/{cardCode}")
    public ApiResponse<PersonDetail> detail(@PathVariable @Pattern(regexp = CARD) String cardCode) {
        return ApiResponse.ok(service.detail(cardCode));
    }

    /** 某天某项体征的原始读数；date 不传是今天。 */
    @GetMapping("/persons/{cardCode}/trend")
    public ApiResponse<Trend> trend(@PathVariable @Pattern(regexp = CARD) String cardCode,
                                    @RequestParam String metric,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                    LocalDate date) {
        if (!ArchiveService.VITALS.contains(metric)) {
            throw BizException.badRequest("metric 只能是 HEART_RATE、SPO2、TEMPERATURE、BLOOD_PRESSURE");
        }
        return ApiResponse.ok(service.trend(cardCode, metric, date));
    }

    /** 到今天为止最近几天的步数（1–31 天）。 */
    @GetMapping("/persons/{cardCode}/steps")
    public ApiResponse<StepDays> steps(@PathVariable @Pattern(regexp = CARD) String cardCode,
                                       @RequestParam(defaultValue = "7") @Min(1) @Max(31) int days) {
        return ApiResponse.ok(service.steps(cardCode, days));
    }

    @GetMapping("/persons/{cardCode}/alerts")
    public ApiResponse<PageResult<AlertRecord>> alerts(@PathVariable @Pattern(regexp = CARD) String cardCode,
                                                       @RequestParam(defaultValue = "1") @Min(1) int page,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.alerts(cardCode, page, size));
    }

    /** @param age 16–75 */
    public record AgeRequest(@NotNull Integer age) {
    }

    /** 录入年龄：唯一允许人工修改的人员字段，写操作日志。 */
    @PutMapping("/persons/{cardCode}/age")
    public ApiResponse<AgeResult> setAge(@PathVariable @Pattern(regexp = CARD) String cardCode,
                                         @Valid @RequestBody AgeRequest req) {
        return ApiResponse.ok(service.setAge(cardCode, req.age()));
    }
}
