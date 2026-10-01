package com.xzkj.hv2.map;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xzkj.hv2.common.api.ApiResponse;
import com.xzkj.hv2.common.api.PageResult;
import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.map.MapViews.MapConfig;
import com.xzkj.hv2.map.MapViews.MapPerson;
import com.xzkj.hv2.map.MapViews.PersonCard;
import com.xzkj.hv2.map.MapViews.Station;
import com.xzkj.hv2.map.MapViews.StationList;
import com.xzkj.hv2.map.MapViews.StationLog;

/** 地图模式、基站摆放的接口（docs/05 第五节、docs/06 第五节）。所有登录用户都能摆放。 */
@RestController
@RequestMapping("/api/map")
public class MapController {

    /** filter 参数 → 名单类型（null 表示不按名单过滤） */
    private static final Map<String, String> FILTERS = Map.of("key", "KEY", "today", "TODAY");
    private static final String CARD = "^[0-9A-Za-z]{17}$";
    private static final String STATION = "^[0-9A-Za-z]{1,22}$";

    private final MapService service;
    private final StationMarkService marks;

    public MapController(MapService service, StationMarkService marks) {
        this.service = service;
        this.marks = marks;
    }

    @GetMapping("/config")
    public ApiResponse<MapConfig> config() {
        return ApiResponse.ok(service.config());
    }

    /** 井下人员及位置；filter 取 all / key（重点监护）/ today（今日关注）。 */
    @GetMapping("/persons")
    public ApiResponse<List<MapPerson>> persons(@RequestParam(defaultValue = "all") String filter,
                                                @RequestParam(required = false) @Size(max = 50) String keyword) {
        if (!"all".equals(filter) && !FILTERS.containsKey(filter)) {
            throw BizException.badRequest("filter 只能是 all、key、today");
        }
        return ApiResponse.ok(service.persons(FILTERS.get(filter), keyword));
    }

    @GetMapping("/persons/{cardCode}/card")
    public ApiResponse<PersonCard> card(@PathVariable @Pattern(regexp = CARD) String cardCode) {
        return ApiResponse.ok(service.card(cardCode));
    }

    /** 全部基站；placed=false 只看未摆放，placed=true 只看已摆放。 */
    @GetMapping("/stations")
    public ApiResponse<StationList> stations(@RequestParam(required = false) Boolean placed) {
        return ApiResponse.ok(service.stations(placed));
    }

    /**
     * 新增或修改摆放。新增时 version 传 0，修改时传读到的版本号。
     *
     * @param x           EPSG:4527 东向坐标（米）
     * @param y           北向坐标（米）
     * @param displayName 用户起的名字，最长 100 字，去首尾空格；空表示不起名（用厂家名称）
     */
    public record MarkRequest(@NotNull BigDecimal x, @NotNull BigDecimal y,
                              @Size(max = 200) String displayName, @NotNull @Min(0) Long version) {
    }

    @PutMapping("/stations/{stationCode}/mark")
    public ApiResponse<Station> saveMark(@PathVariable @Pattern(regexp = STATION) String stationCode,
                                         @Valid @RequestBody MarkRequest req) {
        return ApiResponse.ok(marks.save(stationCode, req.x(), req.y(), req.displayName(), req.version()));
    }

    @DeleteMapping("/stations/{stationCode}/mark")
    public ApiResponse<Void> deleteMark(@PathVariable @Pattern(regexp = STATION) String stationCode,
                                        @RequestParam @Min(1) long version) {
        marks.delete(stationCode, version);
        return ApiResponse.ok();
    }

    /** 摆放操作日志（只读），倒序；stationCode 不传看全部。 */
    @GetMapping("/station-logs")
    public ApiResponse<PageResult<StationLog>> stationLogs(
            @RequestParam(required = false) @Pattern(regexp = STATION) String stationCode,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.stationLogs(stationCode, page, size));
    }
}
