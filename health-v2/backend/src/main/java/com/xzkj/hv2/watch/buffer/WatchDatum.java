package com.xzkj.hv2.watch.buffer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.xzkj.hv2.common.config.TimeConfig;

/**
 * 缓冲里的一条数据，收到时就定下编号、IMEI、卡编码和时间，之后不再改（docs/03 第五节）。以 JSON 存进 Redis。
 * <ul>
 *   <li>{@code V} 体征：metric、val1、val2（血压时是收缩压、舒张压）；</li>
 *   <li>{@code H} 心跳：battery（有效电量，没有则为空）、steps（今天累计，已在收到时算好）、raw（计数器读数）。
 *       未绑定的表只有电量，没有步数。</li>
 * </ul>
 *
 * @param at 收到时间（毫秒）。写库时舍到秒，和库里 TIMESTAMP(0) 一致；步数用毫秒判断先后
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WatchDatum(
        String type,
        long msgId,
        String imei,
        String card,
        long at,
        String metric,
        BigDecimal val1,
        BigDecimal val2,
        Integer battery,
        Long steps,
        Long raw) {

    public static final String VITAL = "V";
    public static final String HEARTBEAT = "H";

    public static WatchDatum vital(long msgId, String imei, String card, long at, String metric,
                                   BigDecimal val1, BigDecimal val2) {
        return new WatchDatum(VITAL, msgId, imei, card, at, metric, val1, val2, null, null, null);
    }

    public static WatchDatum heartbeat(long msgId, String imei, String card, long at, Integer battery,
                                       Long steps, Long raw) {
        return new WatchDatum(HEARTBEAT, msgId, imei, card, at, null, null, null, battery, steps, raw);
    }

    @JsonIgnore
    public boolean isVital() {
        return VITAL.equals(type);
    }

    /** 采集时间（秒以下舍去）。 */
    @JsonIgnore
    public LocalDateTime collectedAt() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(at), TimeConfig.ZONE).truncatedTo(ChronoUnit.SECONDS);
    }

    /** 带毫秒的收到时间，步数判断先后用。 */
    @JsonIgnore
    public LocalDateTime receivedAt() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(at), TimeConfig.ZONE);
    }

    @JsonIgnore
    public LocalDate statDate() {
        return receivedAt().toLocalDate();
    }
}
