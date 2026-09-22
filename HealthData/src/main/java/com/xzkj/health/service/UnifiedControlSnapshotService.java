package com.xzkj.health.service;

import com.xzkj.health.dto.dashboard.UnifiedControlSnapshotView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;

/**
 * Loads the unified-control screen serially through one HTTP request.
 * A failed section is returned as null so one slow optional panel does not blank the whole page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedControlSnapshotService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DashboardService dashboardService;
    private final StatisticsService statisticsService;
    private final RealtimeService realtimeService;
    private final CommandCenterDashboardSummaryService commandCenterDashboardSummaryService;

    public UnifiedControlSnapshotView getSnapshot(String startTime, String endTime, int days,
                                                   String groupBy, String month, String period) {
        String normalizedMonth = normalizeMonth(month);
        // 当日模式只需要当天的按日汇总；若沿用前端传入的 7，会与近7日共用缓存键，
        // 首次空结果还可能把随后到达的当天数据缓存住。
        int dailyTrendDays = "day".equals(normalizePeriod(period)) ? 1 : days;
        return new UnifiedControlSnapshotView(
                load("overview", () -> dashboardService.getCurrentMonthCounts(startTime, endTime)),
                load("bodyIndicators", () -> dashboardService.getCurrentMonthAverage(startTime, endTime)),
                load("deviceActivation", () -> dashboardService.getDeviceActivation(startTime, endTime)),
                load("top5", () -> dashboardService.getDeptTop5(startTime, endTime)),
                load("personCounts", () -> dashboardService.getPersonCounts(startTime, endTime)),
                load("preShift", dashboardService::getPreShiftCompliance),
                load("deptHealthCounts", () -> dashboardService.getDeptHealthCounts(startTime, endTime)),
                load("deptPersonStats", () -> dashboardService.getDeptPersonStats(startTime, endTime)),
                load("dailyTrend", () -> dashboardService.getDailyAnomalyRates(dailyTrendDays)),
                load("warningDistribution", () -> dashboardService.getWarningDistribution(startTime, endTime, groupBy)),
                load("warningTypes", () -> statisticsService.getWarningTypeCounts(normalizedMonth)),
                load("healthSnapshot", realtimeService::getHealthSnapshot),
                load("commandSummary", () -> commandCenterDashboardSummaryService.getSummary(period)),
                LocalDateTime.now().format(DATE_TIME)
        );
    }

    private String normalizeMonth(String month) {
        try {
            return YearMonth.parse(month).toString();
        } catch (DateTimeParseException | NullPointerException ignored) {
            return YearMonth.now().toString();
        }
    }

    private String normalizePeriod(String period) {
        return "week".equals(period) || "month".equals(period) ? period : "day";
    }

    private <T> T load(String section, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            log.warn("统一管控快照局部加载失败: section={}, reason={}", section, exception.getMessage());
            return null;
        }
    }
}
