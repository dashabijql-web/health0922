package com.xzkj.health.dto.dashboard;

import lombok.Data;

@Data
public class DashboardDailyAnomalyRateRow {
    private String date;
    private Number heartRateCount;
    private Number bloodOxygenCount;
    private Number temperatureCount;
    private Number pressureCount;
    private Number heartRateRate;
    private Number bloodOxygenRate;
    private Number temperatureRate;
    private Number pressureRate;
}
