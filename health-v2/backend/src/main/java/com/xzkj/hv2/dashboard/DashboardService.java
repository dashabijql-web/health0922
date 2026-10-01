package com.xzkj.hv2.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xzkj.hv2.alert.AlertLabels;
import com.xzkj.hv2.alert.AlertProperties;
import com.xzkj.hv2.common.api.PageResult;
import com.xzkj.hv2.dashboard.DashboardViews.AlertPerson;
import com.xzkj.hv2.dashboard.DashboardViews.DeviceEvent;
import com.xzkj.hv2.dashboard.DashboardViews.Headcount;
import com.xzkj.hv2.dashboard.DashboardViews.HeadcountSeries;
import com.xzkj.hv2.dashboard.DashboardViews.InWellPerson;
import com.xzkj.hv2.dashboard.DashboardViews.KeyPerson;
import com.xzkj.hv2.dashboard.DashboardViews.Overview;
import com.xzkj.hv2.dashboard.DashboardViews.PortalSummary;
import com.xzkj.hv2.dashboard.DashboardViews.Stat;
import com.xzkj.hv2.dashboard.DashboardViews.StepRank;
import com.xzkj.hv2.positioning.PositioningFreshness;
import com.xzkj.hv2.positioning.PositioningProperties;
import com.xzkj.hv2.positioning.PositioningStatusService;
import com.xzkj.hv2.watch.WatchProperties;

/**
 * 入口页、动态数据页的数字（docs/05 第三、四节）。所有数字在这里算好，前端只显示。
 * "现在""今天"一律取注入的 {@link Clock}。
 */
@Service
public class DashboardService {

    private static final List<String> VITALS = List.of("HEART_RATE", "SPO2", "TEMPERATURE");
    private static final List<String> COUNTED = List.of("HEART_RATE", "SPO2", "TEMPERATURE", "BLOOD_PRESSURE");

    private final DashboardMapper mapper;
    private final PositioningStatusService positioning;
    private final Clock clock;
    private final int onlineWindowMinutes;
    private final int activeMinutes;
    private final int staleMinutes;

    public DashboardService(DashboardMapper mapper, PositioningStatusService positioning, Clock clock,
                            WatchProperties watchProps, AlertProperties alertProps,
                            PositioningProperties positioningProps) {
        this.mapper = mapper;
        this.positioning = positioning;
        this.clock = clock;
        this.onlineWindowMinutes = watchProps.onlineWindowMinutes();
        this.activeMinutes = alertProps.activeMinutes();
        this.staleMinutes = positioningProps.staleMinutes();
    }

    // ---- 入口页 ----

    public PortalSummary portalSummary() {
        LocalDate today = LocalDate.now(clock);
        Map<String, Stat> vitals = new LinkedHashMap<>();
        VITALS.forEach(m -> vitals.put(m, null));
        LocalDateTime dataTime = null;
        for (DashboardMapper.VitalStatRow r : mapper.vitalStats(today)) {
            vitals.put(r.metric(), new Stat(r.maxV(), r.minV(), r.avgV()));
            if (dataTime == null || r.updatedAt().isAfter(dataTime)) {
                dataTime = r.updatedAt();
            }
        }
        DashboardMapper.StepStatRow steps = mapper.stepStats(today);
        vitals.put("STEPS", steps == null || steps.maxV() == null ? null
                : new Stat(steps.maxV(), steps.minV(), steps.avgV()));

        DashboardMapper.WatchListCounts lists = mapper.watchListCounts(today);
        PositioningFreshness fresh = positioning.freshness();
        return new PortalSummary(dataTime, vitals, lists.keyCount(), lists.todayCount(), inWell(fresh),
                mapper.countEnabledDevices(), fresh);
    }

    // ---- 展示模式 ----

    public Overview overview() {
        LocalDateTime dayStart = LocalDate.now(clock).atStartOfDay();
        Map<String, Long> collected = new LinkedHashMap<>();
        COUNTED.forEach(m -> collected.put(m, 0L));
        for (DashboardMapper.CounterRow r : mapper.metricCounters()) {
            collected.put(r.metric(), r.totalCount());
        }
        long total = collected.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Integer> alerts = new LinkedHashMap<>();
        AlertLabels.CATEGORIES.forEach(c -> alerts.put(c, 0));
        for (DashboardMapper.CategoryCount c : mapper.alertPersonCounts(dayStart, dayStart.plusDays(1))) {
            alerts.put(c.category(), c.persons());
        }
        return new Overview(mapper.latestWatchDataTime(), total, collected, alerts, "today",
                mapper.countDeviceEvents(dayStart, dayStart.plusDays(1)));
    }

    /** 某一类告警今天出过事的人（一人一行，条数 = 格子里的人数）。 */
    public PageResult<AlertPerson> alertPersons(String category, int page, int size) {
        LocalDateTime dayStart = LocalDate.now(clock).atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        int total = mapper.countAlertPersons(category, dayStart, dayEnd);
        List<AlertPerson> list = total == 0 ? List.of()
                : mapper.alertPersons(category, dayStart, dayEnd, (page - 1) * size, size).stream()
                .map(r -> new AlertPerson(r.cardCode(), r.deviceImei(), r.personName(), r.dept(), r.eventCount(),
                        r.occurTotal(), r.lastAt(), AlertLabels.codeName(r.lastCode()),
                        // 设备报警的 VAL_TEXT 是手表的报警代码，不是体征值，不显示
                        "THRESHOLD".equals(r.lastSrc()) ? r.lastVal() : null))
                .toList();
        return new PageResult<>(total, page, size, list);
    }

    public PageResult<DeviceEvent> deviceEvents(int page, int size) {
        LocalDateTime dayStart = LocalDate.now(clock).atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        int total = mapper.countDeviceEvents(dayStart, dayEnd);
        List<DeviceEvent> list = total == 0 ? List.of()
                : mapper.deviceEvents(dayStart, dayEnd, (page - 1) * size, size).stream()
                .map(r -> new DeviceEvent(r.id(), r.cardCode(), r.deviceImei(), r.personName(), r.code(),
                        AlertLabels.codeName(r.code()), r.occurredAt(), r.lastOccurredAt(), r.occurCount()))
                .toList();
        return new PageResult<>(total, page, size, list);
    }

    public List<KeyPerson> keyPersons() {
        return mapper.keyPersons().stream().map(r -> new KeyPerson(r.cardCode(), r.personName(), r.dept())).toList();
    }

    // ---- 底部三块 ----

    /**
     * 当天 0 点到现在的井下人数，一份定位文件一个点。两个相邻的点隔得比定位过期阈值还久，说明中间没收到文件，
     * 前端在那里断开、不连线（gapMinutes）。
     */
    public HeadcountSeries headcountSeries(LocalDate date) {
        List<HeadcountSeries.Point> points = mapper.headcountSeries(date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay()).stream()
                .map(p -> new HeadcountSeries.Point(p.snapshotTime(), p.inWellCount()))
                .toList();
        return new HeadcountSeries(date.toString(), staleMinutes, points);
    }

    public Headcount headcount() {
        PositioningFreshness fresh = positioning.freshness();
        if (fresh.dataTime() == null) {
            return new Headcount(null, null, null, null, null, null, fresh);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime activeSince = now.minusMinutes(activeMinutes);
        DashboardMapper.HeadcountRow r = mapper.headcount(onlineSince(now), activeSince,
                activeSince.toLocalDate().atStartOfDay());
        return new Headcount(r.inWell(), r.expected(), r.onlineCount(), r.expected() - r.onlineCount(),
                r.onlineCount() - r.onlineAlert(), r.onlineAlert(), fresh);
    }

    public PageResult<InWellPerson> inWellPersons(String keyword, int page, int size) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.strip();
        int total = mapper.countInWellPersons(kw);
        List<InWellPerson> list = total == 0 ? List.of()
                : mapper.inWellPersons(kw, onlineSince(LocalDateTime.now(clock)), (page - 1) * size, size).stream()
                .map(r -> new InWellPerson(r.cardCode(), r.personName(), r.dept(), r.areaName(), r.watchState()))
                .toList();
        return new PageResult<>(total, page, size, list);
    }

    public List<StepRank> stepsRank(int limit) {
        List<DashboardMapper.StepRankRow> rows = mapper.stepsRank(LocalDate.now(clock), limit);
        long top = rows.isEmpty() ? 0 : rows.getFirst().steps();
        List<StepRank> list = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            DashboardMapper.StepRankRow r = rows.get(i);
            int pct = top <= 0 ? 0 : (int) Math.round(r.steps() * 100.0 / top);
            list.add(new StepRank(i + 1, r.cardCode(), r.personName(), r.dept(), r.steps(), pct));
        }
        return list;
    }

    private Integer inWell(PositioningFreshness fresh) {
        return fresh.dataTime() == null ? null : mapper.countInWell();
    }

    private LocalDateTime onlineSince(LocalDateTime now) {
        return now.minusMinutes(onlineWindowMinutes);
    }
}
