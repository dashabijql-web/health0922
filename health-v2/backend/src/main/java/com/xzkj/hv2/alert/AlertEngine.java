package com.xzkj.hv2.alert;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;

import com.xzkj.hv2.common.config.TimeConfig;
import com.xzkj.hv2.common.db.DbErrors;

/**
 * 预警（docs/03 第四节）。数据通过有效性检查后立刻判断，不等写库；事件量小、又要尽快上大屏，直接写 ALERT_EVENT。
 * <ul>
 *   <li>体征越界 THRESHOLD：按这个人所属岗位类别的阈值判断（{@link AlertRules}）；</li>
 *   <li>设备报警 DEVICE：手表 AP10 主动上报。SOS 只来自 AP10 的 01。</li>
 * </ul>
 * <b>去重</b>：同一个人（没绑定时按设备号）、同一事件代码，距上次发生不到去重间隔，就不新建事件，
 * 只更新最近发生时间和次数；事件不跨天，过了 0 点再发生算新的一条。去重以 ALERT_EVENT 为准：
 * 启动时把当天仍在间隔内的事件读进内存；同一个人同一代码的判断和写入加锁串行，避免两个线程同时新建。
 * 告警只是"发生过"的记录，没有处置状态。
 */
@Service
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);
    private static final int LOCK_STRIPES = 64;

    private final AlertMapper mapper;
    private final AlertRules rules;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Duration thresholdDedup;
    private final Duration deviceDedup;
    private final Duration otherDedup;
    private final Map<String, Active> active = new ConcurrentHashMap<>();
    private final Object[] locks = new Object[LOCK_STRIPES];
    private volatile boolean loaded;

    public AlertEngine(AlertMapper mapper, AlertRules rules, AlertProperties props, Clock clock, MeterRegistry meters) {
        this.mapper = mapper;
        this.rules = rules;
        this.clock = clock;
        this.meters = meters;
        this.thresholdDedup = Duration.ofMinutes(props.dedupMinutes());
        this.deviceDedup = Duration.ofMinutes(props.deviceDedupMinutes());
        this.otherDedup = Duration.ofMinutes(props.dedupMinutes());
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    /**
     * 判断一项体征（已通过有效性检查、手表已绑定）。血压分收缩压、舒张压分别判断。
     *
     * @param metric HEART_RATE / SPO2 / TEMPERATURE / BLOOD_PRESSURE
     */
    public void checkVital(String cardCode, String imei, String metric, BigDecimal val1, BigDecimal val2,
                           long atMillis) {
        switch (metric) {
            case "HEART_RATE" -> judge(cardCode, imei, VitalCode.HR, val1, plain(val1), atMillis);
            case "SPO2" -> judge(cardCode, imei, VitalCode.SPO2, val1, plain(val1), atMillis);
            case "TEMPERATURE" -> judge(cardCode, imei, VitalCode.TEMP, val1, plain(val1), atMillis);
            case "BLOOD_PRESSURE" -> {
                String text = plain(val1) + "/" + plain(val2);
                judge(cardCode, imei, VitalCode.BP_SYS, val1, text, atMillis);
                judge(cardCode, imei, VitalCode.BP_DIA, val2, text, atMillis);
            }
            default -> {
                // 不是体征
            }
        }
    }

    /**
     * 手表报警包 AP10。
     *
     * @param cardCode 未登记或未绑定时为 null，事件按设备号区分
     * @return 是否是认识的报警代码（00 无报警也算认识）
     */
    public boolean deviceAlarm(String cardCode, String imei, String alarmCode, long atMillis) {
        DeviceAlarm alarm = DeviceAlarm.of(alarmCode);
        if (alarm == null) {
            return false;
        }
        if (alarm != DeviceAlarm.NONE) {
            Duration dedup = alarm.category.equals("OTHER") ? otherDedup : deviceDedup;
            record(cardCode, imei, "DEVICE", alarm.code, alarm.category, alarm.severity, alarmCode, null, dedup,
                    atMillis);
        }
        return true;
    }

    private void judge(String cardCode, String imei, VitalCode vc, BigDecimal value, String text, long atMillis) {
        AlertRules.Rule rule = rules.ruleFor(cardCode, vc.ruleMetric);
        if (rule == null) {
            return;
        }
        int j = rule.judge(value);
        if (j != 0) {
            String code = vc.codePrefix + (j < 0 ? "_LOW" : "_HIGH");
            record(cardCode, imei, "THRESHOLD", code, vc.category, rule.severity(), text, rule.group(), thresholdDedup,
                    atMillis);
        }
    }

    private void record(String cardCode, String imei, String src, String code, String category, int severity,
                        String valText, String ruleGroup, Duration dedup, long atMillis) {
        ensureLoaded();
        LocalDateTime at = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), TimeConfig.ZONE)
                .truncatedTo(ChronoUnit.SECONDS);
        String key = key(cardCode, imei, code);
        synchronized (locks[Math.floorMod(key.hashCode(), LOCK_STRIPES)]) {
            try {
                Active a = active.get(key);
                if (a != null && a.day.equals(at.toLocalDate()) && Duration.between(a.lastAt, at).compareTo(dedup) < 0
                        && mapper.touchEvent(a.id, at) == 1) {
                    active.put(key, new Active(a.id, a.day, at.isAfter(a.lastAt) ? at : a.lastAt));
                    return;
                }
                // 没有可合并的事件（或内存里记着的那条在库里已经不在了）：新建一条
                AlertEventEntry e = new AlertEventEntry(cardCode, src, code, category, severity, valText, ruleGroup,
                        at, imei);
                mapper.insertEvent(e);
                active.put(key, new Active(e.getId(), at.toLocalDate(), at));
                meters.counter("health.alert.created", "src", src, "category", category).increment();
            } catch (DataAccessException ex) {
                log.error("写预警事件失败（{} {}）：{}", src, code, DbErrors.code(ex));
            }
        }
    }

    /** 132.0 → 132，37.5 → 37.5 */
    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** 去重的键：同一个人（没绑定时按设备号）、同一事件代码。 */
    private static String key(String cardCode, String imei, String code) {
        return (cardCode != null ? cardCode : "imei:" + imei) + "|" + code;
    }

    /** 启动时把当天仍在去重间隔内的事件读进内存；数据库连不上时下次再试。 */
    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (loaded) {
                return;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            Duration longest = thresholdDedup.compareTo(deviceDedup) >= 0 ? thresholdDedup : deviceDedup;
            try {
                for (AlertMapper.ActiveEventRow r : mapper.selectRecentEvents(now.toLocalDate().atStartOfDay(),
                        now.minus(longest))) {
                    active.put(key(r.cardCode(), r.deviceImei(), r.code()),
                            new Active(r.id(), r.occurredAt().toLocalDate(), r.lastOccurredAt()));
                }
                loaded = true;
            } catch (DataAccessException e) {
                log.warn("读入今天的预警事件失败，稍后重试：{}", e.getMessage());
            }
        }
    }

    /** 过了去重间隔的记录不再需要，定时清掉，免得内存一直涨。 */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void evictExpired() {
        LocalDateTime now = LocalDateTime.now(clock);
        Duration longest = thresholdDedup.compareTo(deviceDedup) >= 0 ? thresholdDedup : deviceDedup;
        active.values().removeIf(a -> a.lastAt.isBefore(now.minus(longest)) || !a.day.equals(now.toLocalDate()));
    }

    private record Active(long id, LocalDate day, LocalDateTime lastAt) {
    }

    /** 体征越界：阈值表里的指标名、事件代码前缀、大屏分类。 */
    private enum VitalCode {
        HR("HEART_RATE", "HR", "HEART_RATE"),
        SPO2("SPO2", "SPO2", "SPO2"),
        TEMP("TEMPERATURE", "TEMP", "TEMPERATURE"),
        BP_SYS("BP_SYS", "BP_SYS", "BLOOD_PRESSURE"),
        BP_DIA("BP_DIA", "BP_DIA", "BLOOD_PRESSURE");

        final String ruleMetric;
        final String codePrefix;
        final String category;

        VitalCode(String ruleMetric, String codePrefix, String category) {
            this.ruleMetric = ruleMetric;
            this.codePrefix = codePrefix;
            this.category = category;
        }
    }
}
