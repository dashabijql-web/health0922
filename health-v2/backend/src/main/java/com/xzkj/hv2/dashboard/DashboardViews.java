package com.xzkj.hv2.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.xzkj.hv2.positioning.PositioningFreshness;

/**
 * 入口页、动态数据页接口的返回（docs/05 第三、四节）。拿不到的数据是 null，前端显示"暂无数据"，不显示 0。
 * 人员一律带完整 17 位卡编码 cardCode，页面显示后 5 位（卡号）。
 */
public final class DashboardViews {

    private DashboardViews() {
    }

    /** 最大、最小、平均；今天没有数据时整项为 null。 */
    public record Stat(BigDecimal max, BigDecimal min, BigDecimal avg) {
    }

    /**
     * GET /api/portal/summary。
     *
     * @param dataTime        体征统计（日汇总）最近一次重算的时间；今天还没有汇总时为 null
     * @param vitals          HEART_RATE、SPO2、TEMPERATURE、STEPS 四项
     * @param inWellCount     当前井下人数；从没收到过 RYSS 时为 null
     */
    public record PortalSummary(LocalDateTime dataTime, Map<String, Stat> vitals, int keyPersonCount,
                                int todayWatchCount, Integer inWellCount, int deviceCount,
                                PositioningFreshness positioning) {
    }

    /**
     * GET /api/dashboard/overview。
     *
     * @param dataTime         手表数据时间：库里最新一条体征的采集时间；还没有任何体征时为 null
     * @param alerts           六类告警今天的人数（同一个人多条事件算 1 个）
     * @param deviceEventCount 今天的设备事件条数
     */
    public record Overview(LocalDateTime dataTime, long totalCollected, Map<String, Long> collected,
                           Map<String, Integer> alerts, String alertWindow, int deviceEventCount) {
    }

    /**
     * 某一类告警今天出过事的人，一人一行。
     *
     * @param cardCode   未绑定的表为 null，这时看 deviceImei
     * @param eventCount 今天这一类的事件条数
     * @param occurTotal 今天这一类一共发生的次数（去重期间的重复也算）
     * @param lastEvent  最近一条事件的名称，如"心率偏高"
     * @param lastValue  最近一条体征越界事件第一次触发时的值，如 132 或 152/98；设备报警（SOS、跌倒）为 null
     */
    public record AlertPerson(String cardCode, String deviceImei, String name, String dept, int eventCount,
                              int occurTotal, LocalDateTime lastAt, String lastEvent, String lastValue) {
    }

    /**
     * 设备事件（低电、脱落……）一条。
     *
     * @param occurCount 一段持续的事件只算一条，这里是它重复发生的次数
     */
    public record DeviceEvent(long id, String cardCode, String deviceImei, String name, String code, String event,
                              LocalDateTime occurredAt, LocalDateTime lastOccurredAt, int occurCount) {
    }

    public record KeyPerson(String cardCode, String name, String dept) {
    }

    /** GET /api/dashboard/headcount-series：一份定位文件一个点。 */
    public record HeadcountSeries(String date, int gapMinutes, List<Point> points) {

        public record Point(LocalDateTime time, int inWell) {
        }
    }

    /**
     * GET /api/dashboard/headcount。从没收到过 RYSS 时六个人数都是 null。
     * 已上线无告警 + 已上线告警 = 已上线；未上线 = 应上线 − 已上线。
     */
    public record Headcount(Integer inWell, Integer expected, Integer online, Integer offline, Integer onlineNormal,
                            Integer onlineAlert, PositioningFreshness positioning) {
    }

    /** @param watchState ONLINE 在线 / OFFLINE 离线 / UNBOUND 没绑定手表 */
    public record InWellPerson(String cardCode, String name, String dept, String areaName, String watchState) {
    }

    /** @param barPct 进度条长度：步数 ÷ 第一名的步数 × 100，第一名为 0 步时都是 0 */
    public record StepRank(int rank, String cardCode, String name, String dept, long steps, int barPct) {
    }
}
