package com.xzkj.hv2.positioning;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xzkj.hv2.common.api.ApiResponse;

@RestController
@RequestMapping("/api/positioning")
public class PositioningController {

    private final PositioningStatusService statusService;

    public PositioningController(PositioningStatusService statusService) {
        this.statusService = statusService;
    }

    /** 每种定位文件最后一次的处理情况、数据时间、是否过期（docs/05 运维接口）。 */
    @GetMapping("/status")
    public ApiResponse<PositioningStatus> status() {
        return ApiResponse.ok(statusService.status());
    }
}
