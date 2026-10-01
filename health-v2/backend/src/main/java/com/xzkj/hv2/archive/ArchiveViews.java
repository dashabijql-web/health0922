package com.xzkj.hv2.archive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 健康档案、个人档案、名单接口的返回（docs/05 第六～八节）。拿不到的数据是 null，前端显示"暂无数据"或"未录入"。
 */
public final class ArchiveViews {

    private ArchiveViews() {
    }

    /** GET /api/archive/filters：下拉框的选项，只取绑定了手表的人（和列表口径一致），按拼音排序。 */
    public record Filters(List<String> depts, List<String> jobKinds) {
    }

    /**
     * 健康档案的一张卡片。
     *
     * @param age 人工录入的年龄；没录入为 null（显示"未录入"）
     */
    public record ArchivePerson(String cardCode, String name, String jobKind, String dept, Integer age) {
    }

    /**
     * GET /api/archive/persons/{cardCode}：个人档案。
     *
     * @param ageUpdatedBy 谁录入的年龄；没录入为 null
     * @param vitals       HEART_RATE、SPO2、TEMPERATURE、BLOOD_PRESSURE 四项最新值，从没测过的为 null
     * @param battery      电量（来自心跳）；没绑定手表或没收到过为 null
     * @param steps        今天的步数；今天没有记录为 null
     * @param position     当前位置；不在井下为 null
     * @param lists        KEY（重点监护）、TODAY（今日关注）：在名单里时是那一条，不在为 null（过期的今日关注算不在）
     */
    public record PersonDetail(String cardCode, String name, String jobKind, String dept, Integer age,
                               String ageUpdatedBy, LocalDateTime ageUpdatedAt, Watch watch, Battery battery,
                               Map<String, Vital> vitals, TodaySteps steps, Position position,
                               Map<String, ListEntry> lists) {
    }

    /**
     * @param state      ONLINE 在线 / OFFLINE 离线 / UNBOUND 没绑定（启用的）手表，口径同大屏人员表
     * @param lastSeenAt 手表最后一次上行的时间；没绑定或从没上行过为 null
     */
    public record Watch(String state, LocalDateTime lastSeenAt) {
    }

    public record Battery(int pct, LocalDateTime time) {
    }

    /**
     * @param value  心率、血氧、体温，血压时是收缩压
     * @param value2 血压的舒张压，其余为 null
     * @param stale  超过"数据较旧"阈值（WATCH_VITAL_STALE_MINUTES）没更新
     */
    public record Vital(BigDecimal value, BigDecimal value2, LocalDateTime collectedAt, boolean stale) {
    }

    /** @param updatedAt 最近一次收到步数的时间 */
    public record TodaySteps(long steps, LocalDateTime updatedAt) {
    }

    /**
     * @param stationName     基站显示名称（用户起的名字 &gt; 厂家名称 &gt; 区域名称 + 编码后 6 位）
     * @param posTime         定位时间：这条位置依据的那份 RYSS 的数据上传时间
     * @param stationAbnormal 所在基站"通讯中断"或"故障"，位置可能不准
     */
    public record Position(String areaName, String stationName, boolean placed, LocalDateTime posTime,
                           boolean stationAbnormal) {
    }

    /** 名单里的一条。expireDate：今日关注是当天，重点监护为 null。 */
    public record ListEntry(long id, String note, LocalDate expireDate, String addedBy, LocalDateTime addedAt) {
    }

    /**
     * GET /api/archive/persons/{cardCode}/trend：某天某项体征的全部读数（原始值，按时间排）。
     *
     * @param gapMinutes 相邻两点隔得比它久，说明中间没有数据，折线在那里断开（= WATCH_VITAL_STALE_MINUTES）
     */
    public record Trend(String metric, LocalDate date, int gapMinutes, List<TrendPoint> points) {
    }

    /** @param value2 血压的舒张压，其余为 null */
    public record TrendPoint(LocalDateTime time, BigDecimal value, BigDecimal value2) {
    }

    /** GET /api/archive/persons/{cardCode}/steps：从早到晚每天一项，那天没有记录时 steps 为 null。 */
    public record StepDays(List<StepDay> days) {
    }

    public record StepDay(LocalDate date, Long steps) {
    }

    /**
     * 预警记录一条（六类告警和设备事件都在内）。
     *
     * @param src      THRESHOLD 体征越界 / DEVICE 设备报警
     * @param category SOS、FALL、HEART_RATE、BLOOD_PRESSURE、SPO2、TEMPERATURE；设备事件为 OTHER
     * @param event    事件名称，如"心率偏高""低电"
     * @param value    体征越界时触发的值（如 132、152/98）；设备报警为 null
     * @param count    去重期间一共发生了几次
     */
    public record AlertRecord(long id, LocalDateTime occurredAt, LocalDateTime lastOccurredAt, String src,
                              String category, String event, String value, int count) {
    }

    /** PUT /api/archive/persons/{cardCode}/age 的返回。 */
    public record AgeResult(int age, String updatedBy, LocalDateTime updatedAt) {
    }

    /**
     * 名单（GET /api/watch-list）的一条。
     *
     * @param type KEY 重点监护 / TODAY 今日关注
     */
    public record WatchListItem(long id, String cardCode, String name, String dept, String type, String note,
                                LocalDate expireDate, String addedBy, LocalDateTime addedAt) {
    }
}
