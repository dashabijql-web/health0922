package com.xzkj.hv2.map;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * 地图模式、基站摆放接口的返回（docs/05 第五节、docs/06 第五节）。拿不到的数据是 null，前端显示"暂无数据"。
 * 坐标是 EPSG:4527 的原始数字（先东后北，单位米）。
 */
public final class MapViews {

    private MapViews() {
    }

    /**
     * GET /api/map/config。
     *
     * @param wmsVersion 固定 1.1.1：EPSG:4527 官方轴顺序是先北后东，WMS 1.3.0 会按它理解坐标，底图错位（docs/06 第二节）
     * @param extent     [minX, minY, maxX, maxY]
     */
    public record MapConfig(String wmsUrl, String wmsVersion, String lineLayer, String labelLayer, String projection,
                            List<BigDecimal> extent) {
    }

    /**
     * GET /api/map/persons 的一个人（井下的人）。
     *
     * @param stationName 基站显示名称（用户起的名字 &gt; 厂家名称 &gt; 区域名称 + 编码后 6 位）
     * @param placed      所在基站摆放了没有；没摆放时 x、y 为 null，地图上不画
     * @param posTime     定位时间：这条位置依据的那份 RYSS 的数据上传时间
     * @param watchState  ONLINE 在线 / OFFLINE 离线 / UNBOUND 没绑定手表（口径同大屏人员表）
     * @param alerting    有"仍在发生"的六类告警（口径同大屏"已上线-告警"，docs/05 第四节），图标用醒目颜色
     */
    public record MapPerson(String cardCode, String name, String dept, String areaName, String stationCode,
                            String stationName, boolean placed, BigDecimal x, BigDecimal y, LocalDateTime posTime,
                            String watchState, boolean alerting) {
    }

    /**
     * GET /api/map/persons/{cardCode}/card：体征卡。
     *
     * @param position 当前位置；不在井下时为 null
     * @param vitals   HEART_RATE、SPO2、TEMPERATURE、BLOOD_PRESSURE 四项，从没测过的为 null
     * @param battery  电量（来自心跳）；没绑定手表或没收到过时为 null
     * @param alerts   最近 3 条告警（六类），最新的在前
     */
    public record PersonCard(String cardCode, String name, String dept, Position position, Map<String, Vital> vitals,
                             Battery battery, Watch watch, List<RecentAlert> alerts) {
    }

    /**
     * @param stationEnterTime 到达这个基站的时间（RYSS 里的记录）
     * @param stationAbnormal  所在基站"通讯中断"或"故障"，位置可能不准（docs/06 第六节）
     */
    public record Position(String areaName, String stationCode, String stationName, boolean placed, BigDecimal x,
                           BigDecimal y, LocalDateTime posTime, LocalDateTime stationEnterTime,
                           Integer stationRunStatus, boolean stationAbnormal) {
    }

    /**
     * @param value  心率、血氧、体温，血压时是收缩压
     * @param value2 血压的舒张压，其余为 null
     * @param stale  超过"数据较旧"阈值（WATCH_VITAL_STALE_MINUTES）没更新
     */
    public record Vital(BigDecimal value, BigDecimal value2, LocalDateTime collectedAt, boolean stale) {
    }

    public record Battery(int pct, LocalDateTime time) {
    }

    /** @param lastSeenAt 手表最后一次上行的时间；没绑定或从没上行过为 null */
    public record Watch(String state, LocalDateTime lastSeenAt) {
    }

    /** @param value 体征越界时触发的值（如 132、152/98）；SOS、跌倒为 null */
    public record RecentAlert(LocalDateTime occurredAt, LocalDateTime lastOccurredAt, String category, String event,
                              String value) {
    }

    /**
     * GET /api/map/stations：全部基站和摆放情况。
     *
     * @param total    基站总数（不受 placed 筛选影响）
     * @param unplaced 未摆放的基站数（不受 placed 筛选影响）
     */
    public record StationList(int total, int unplaced, List<Station> list) {
    }

    /**
     * 一个基站。
     *
     * @param name        显示名称：用户起的名字 &gt; 厂家名称 &gt; 区域名称 + 编码后 6 位（docs/04 POS_STATION_MARK）
     * @param vendorName  厂家名称（只读），可为空
     * @param displayName 用户起的名字，可为空
     * @param runStatus   0 通讯正常、1 通讯中断、2 故障、9 未知；还没收到过状态为 null
     * @param version     摆放的版本号，改、删时带回来；未摆放时为 0
     */
    public record Station(String stationCode, String name, String vendorName, String displayName, String areaCode,
                          String areaName, Integer runStatus, boolean placed, BigDecimal x, BigDecimal y,
                          long version, String updatedBy, LocalDateTime updatedAt) {
    }

    /**
     * 摆放操作日志一条（只读）。
     *
     * @param action STATION_PLACE 摆放 / STATION_MOVE 移动 / STATION_RENAME 改名 / STATION_DELETE 删除
     * @param before 改动前的 { x, y, displayName }，摆放时为 null
     * @param after  改动后的 { x, y, displayName }，删除时为 null
     */
    public record StationLog(long id, String username, String action, String stationCode, String stationName,
                             JsonNode before, JsonNode after, LocalDateTime createdAt) {
    }
}
