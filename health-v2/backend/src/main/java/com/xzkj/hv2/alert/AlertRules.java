package com.xzkj.hv2.alert;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 阈值按岗位类别分（docs/03 第四节）。启动时读入内存，之后每分钟重新读一次，改表后最多 1 分钟生效：
 * <ol>
 *   <li>由卡编码查到这个人的工种（POS_PERSON.JOB_KIND）；</li>
 *   <li>由工种查到岗位类别（JOB_KIND_GROUP）；工种没归类、工种为空，都用默认类别 DEFAULT；</li>
 *   <li>取这个类别该指标的阈值（ALERT_RULE）；这个类别没配这一项，就用 DEFAULT 的。</li>
 * </ol>
 * 数据库暂时连不上时继续用上次读到的。
 */
@Component
public class AlertRules {

    public static final String DEFAULT_GROUP = "DEFAULT";

    private static final Logger log = LoggerFactory.getLogger(AlertRules.class);

    private final AlertMapper mapper;
    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), Map.of());
    private volatile boolean loaded;
    private volatile long lastAttempt = System.nanoTime() - RETRY_NANOS;
    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(10);

    public AlertRules(AlertMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 这个人这项指标用哪条阈值。
     *
     * @param cardCode 卡编码
     * @param metric   HEART_RATE / SPO2 / TEMPERATURE / BP_SYS / BP_DIA
     * @return 阈值；DEFAULT 也没配这一项时为 null（不判断）
     */
    public Rule ruleFor(String cardCode, String metric) {
        if (!loaded && System.nanoTime() - lastAttempt >= RETRY_NANOS) {
            // 启动时数据库连不上：处理数据时顺带重试，最多 10 秒一次
            refresh();
        }
        Snapshot s = snapshot;
        String kind = cardCode == null ? null : s.personKinds.get(cardCode);
        String group = kind == null ? DEFAULT_GROUP : s.kindGroups.getOrDefault(kind, DEFAULT_GROUP);
        Rule rule = s.rules.get(group + "|" + metric);
        return rule != null ? rule : s.rules.get(DEFAULT_GROUP + "|" + metric);
    }

    @Scheduled(fixedDelayString = "${hv2.alert.rule-refresh-seconds}", timeUnit = TimeUnit.SECONDS)
    public synchronized void refresh() {
        lastAttempt = System.nanoTime();
        try {
            Map<String, Rule> rules = new HashMap<>();
            for (AlertMapper.RuleRow r : mapper.selectRules()) {
                rules.put(r.groupCode() + "|" + r.metric(),
                        new Rule(r.groupCode(), r.lowLimit(), r.highLimit(), r.severity(), r.enabled() == 1));
            }
            Map<String, String> kindGroups = new HashMap<>();
            mapper.selectKindGroups().forEach(k -> kindGroups.put(k.jobKind(), k.groupCode()));
            Map<String, String> personKinds = new HashMap<>();
            mapper.selectPersonKinds().forEach(p -> personKinds.put(p.cardCode(), p.jobKind()));
            snapshot = new Snapshot(Map.copyOf(rules), Map.copyOf(kindGroups), Map.copyOf(personKinds));
            loaded = true;
        } catch (DataAccessException e) {
            log.warn("刷新预警阈值失败，继续用上次的：{}", e.getMessage());
        }
    }

    /**
     * 一条阈值。
     *
     * @param group 这条阈值属于哪个岗位类别（记进事件的 RULE_GROUP）
     */
    public record Rule(String group, BigDecimal low, BigDecimal high, int severity, boolean enabled) {

        /** 偏低返回 -1，偏高返回 1，正常或不判断返回 0。 */
        public int judge(BigDecimal v) {
            if (!enabled) {
                return 0;
            }
            if (low != null && v.compareTo(low) < 0) {
                return -1;
            }
            if (high != null && v.compareTo(high) > 0) {
                return 1;
            }
            return 0;
        }
    }

    private record Snapshot(Map<String, Rule> rules, Map<String, String> kindGroups, Map<String, String> personKinds) {
    }
}
