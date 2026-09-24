package com.xzkj.health.dto.sleep;

import java.util.List;

public record SleepPageDataView(
        SleepOverviewView overview,
        List<SleepLegendItemView> durationLegend,
        List<SleepLegendItemView> categoryLegend,
        List<SleepDeptUploadView> deptUpload,
        List<SleepDetailItemView> detailList,
        int durationTotal,
        /** 昨夜睡眠不足6小时的人员（最多20人，按睡眠时长从少到多） */
        List<SleepAlertView> alertList,
        /** 昨夜睡眠不足6小时的总人数（alertList 可能被截断，人数以此为准） */
        int alertTotal
) {
}
