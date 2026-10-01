package com.xzkj.hv2.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xzkj.hv2.common.api.ApiResponse;
import com.xzkj.hv2.dashboard.DashboardViews.PortalSummary;

/** 入口页（docs/05 第三节）。 */
@RestController
@RequestMapping("/api/portal")
public class PortalController {

    private final DashboardService service;

    public PortalController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<PortalSummary> summary() {
        return ApiResponse.ok(service.portalSummary());
    }
}
