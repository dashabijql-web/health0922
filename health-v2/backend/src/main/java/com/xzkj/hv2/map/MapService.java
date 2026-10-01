package com.xzkj.hv2.map;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.xzkj.hv2.alert.AlertLabels;
import com.xzkj.hv2.alert.AlertProperties;
import com.xzkj.hv2.common.api.PageResult;
import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.map.MapViews.Battery;
import com.xzkj.hv2.map.MapViews.MapConfig;
import com.xzkj.hv2.map.MapViews.MapPerson;
import com.xzkj.hv2.map.MapViews.PersonCard;
import com.xzkj.hv2.map.MapViews.Position;
import com.xzkj.hv2.map.MapViews.RecentAlert;
import com.xzkj.hv2.map.MapViews.Station;
import com.xzkj.hv2.map.MapViews.StationList;
import com.xzkj.hv2.map.MapViews.StationLog;
import com.xzkj.hv2.map.MapViews.Vital;
import com.xzkj.hv2.map.MapViews.Watch;
import com.xzkj.hv2.watch.WatchProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 地图模式的查询（docs/05 第五节）：底图配置、井下人员位置、体征卡、基站列表、摆放日志。
 * "现在""今天"一律取注入的 {@link Clock}。改动基站摆放见 {@link StationMarkService}。
 */
@Service
public class MapService {

    /** 体征卡展开的告警条数（docs/05 第五节"告警记录（展开最近 3 条）"） */
    static final int CARD_ALERTS = 3;
    private static final List<String> VITALS = List.of("HEART_RATE", "SPO2", "TEMPERATURE", "BLOOD_PRESSURE");

    private final MapMapper mapper;
    private final MapProperties props;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final int onlineWindowMinutes;
    private final int vitalStaleMinutes;
    private final int activeMinutes;

    public MapService(MapMapper mapper, MapProperties props, Clock clock, ObjectMapper objectMapper,
                      WatchProperties watchProps, AlertProperties alertProps) {
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.onlineWindowMinutes = watchProps.onlineWindowMinutes();
        this.vitalStaleMinutes = watchProps.vitalStaleMinutes();
        this.activeMinutes = alertProps.activeMinutes();
    }

    public MapConfig config() {
        MapProperties.Extent e = props.extent();
        return new MapConfig(props.wmsUrl(), "1.1.1", props.lineLayer(), props.labelLayer(), props.projection(),
                List.of(e.minX(), e.minY(), e.maxX(), e.maxY()));
    }

    /**
     * 井下的人和位置。
     *
     * @param list    null 全部 / KEY 重点监护 / TODAY 今日关注（今日关注只算今天还有效的）
     * @param keyword 姓名或卡编码"包含"
     */
    public List<MapPerson> persons(String list, String keyword) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime activeSince = now.minusMinutes(activeMinutes);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.strip();
        return mapper.inWellPersons(list, kw, now.toLocalDate(), now.minusMinutes(onlineWindowMinutes), activeSince,
                        activeSince.toLocalDate().atStartOfDay()).stream()
                .map(r -> new MapPerson(r.cardCode(), r.personName(), r.dept(), r.areaName(), r.stationCode(),
                        StationNames.resolve(r.displayName(), r.vendorName(), r.stationAreaName(), r.stationCode()),
                        r.x() != null, r.x(), r.y(), r.posTime(), r.watchState(), r.alerting() == 1))
                .toList();
    }

    public PersonCard card(String cardCode) {
        MapMapper.CardRow r = mapper.card(cardCode);
        if (r == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "没有这个人");
        }
        LocalDateTime now = LocalDateTime.now(clock);

        Position position = r.inWell() == 0 ? null
                : new Position(r.areaName(), r.stationCode(),
                StationNames.resolve(r.displayName(), r.vendorName(), r.stationAreaName(), r.stationCode()),
                r.x() != null, r.x(), r.y(), r.posTime(), r.stationEnterTime(), r.runStatus(),
                r.runStatus() != null && (r.runStatus() == 1 || r.runStatus() == 2));

        Map<String, Vital> vitals = new LinkedHashMap<>();
        VITALS.forEach(m -> vitals.put(m, null));
        LocalDateTime staleBefore = now.minusMinutes(vitalStaleMinutes);
        for (MapMapper.VitalRow v : mapper.latestVitals(cardCode)) {
            vitals.put(v.metric(), new Vital(v.val1(), v.val2(), v.collectedAt(),
                    v.collectedAt().isBefore(staleBefore)));
        }

        Watch watch;
        Battery battery = null;
        if (r.imei() == null) {
            watch = new Watch("UNBOUND", null);
        } else {
            boolean online = r.lastSeenAt() != null && !r.lastSeenAt().isBefore(now.minusMinutes(onlineWindowMinutes));
            watch = new Watch(online ? "ONLINE" : "OFFLINE", r.lastSeenAt());
            if (r.batteryPct() != null) {
                battery = new Battery(r.batteryPct(), r.batteryTime());
            }
        }

        List<RecentAlert> alerts = mapper.recentAlerts(cardCode, CARD_ALERTS).stream()
                .map(a -> new RecentAlert(a.occurredAt(), a.lastOccurredAt(), a.category(),
                        AlertLabels.codeName(a.code()),
                        // 设备报警的 VAL_TEXT 是手表的报警代码，不是体征值，不显示
                        "THRESHOLD".equals(a.src()) ? a.valText() : null))
                .toList();
        return new PersonCard(r.cardCode(), r.personName(), r.dept(), position, vitals, battery, watch, alerts);
    }

    /** @param placed null 全部 / true 已摆放 / false 未摆放 */
    public StationList stations(Boolean placed) {
        List<Station> all = mapper.stations().stream().map(MapService::toStation).toList();
        int unplaced = (int) all.stream().filter(s -> !s.placed()).count();
        List<Station> list = placed == null ? all : all.stream().filter(s -> s.placed() == placed).toList();
        return new StationList(all.size(), unplaced, list);
    }

    Station station(String stationCode) {
        MapMapper.StationRow r = mapper.station(stationCode);
        return r == null ? null : toStation(r);
    }

    public PageResult<StationLog> stationLogs(String stationCode, int page, int size) {
        String code = stationCode == null || stationCode.isBlank() ? null : stationCode.strip();
        int total = mapper.countStationLogs(code);
        List<StationLog> list = total == 0 ? List.of()
                : mapper.stationLogs(code, (page - 1) * size, size).stream()
                .map(r -> new StationLog(r.id(), r.username(), r.action(), r.targetId(),
                        StationNames.resolve(r.displayName(), r.vendorName(), r.areaName(), r.targetId()),
                        json(r.beforeJson()), json(r.afterJson()), r.createdAt()))
                .toList();
        return new PageResult<>(total, page, size, list);
    }

    private static Station toStation(MapMapper.StationRow r) {
        boolean placed = r.x() != null;
        return new Station(r.stationCode(),
                StationNames.resolve(r.displayName(), r.vendorName(), r.areaName(), r.stationCode()),
                r.vendorName(), r.displayName(), r.areaCode(), r.areaName(), r.runStatus(), placed, r.x(), r.y(),
                placed ? r.version() : 0, placed ? r.updatedBy() : null, placed ? r.updatedAt() : null);
    }

    private JsonNode json(String text) {
        return text == null ? null : objectMapper.readTree(text);
    }
}
