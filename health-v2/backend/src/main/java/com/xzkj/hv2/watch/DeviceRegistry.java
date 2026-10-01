package com.xzkj.hv2.watch;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xzkj.hv2.common.config.TimeConfig;

/**
 * 手表登记与绑定的内存副本，以及每块表的最后上行时间（docs/03 第二节、第五节）。
 * <ul>
 *   <li>"IMEI → 卡编码"每 60 秒从 DEVICE 表刷新一次；后台修改绑定后调用 {@link #refresh()} 立即刷新。
 *       每个包处理时查它，把当时的卡编码写进数据，所以换绑后新数据马上记到新的人名下，不用等手表重连。</li>
 *   <li>停用（STATUS=0）的表按未登记处理。</li>
 *   <li>最后上行时间在内存里随每个包刷新，每轮写库时把变化的一起写进 DEVICE.LAST_SEEN_AT。</li>
 * </ul>
 */
@Component
public class DeviceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistry.class);

    /** 已登记、启用的手表；值是卡编码，未绑定时为空串（ConcurrentHashMap 不能存 null） */
    private volatile Map<String, String> devices = Map.of();
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private volatile boolean loaded;
    private volatile long lastLoadAttempt = Long.MIN_VALUE / 2;
    private static final long RETRY_MILLIS = 10_000;

    private final WatchMapper mapper;
    private final WatchProperties props;
    private final Clock clock;

    public DeviceRegistry(WatchMapper mapper, WatchProperties props, Clock clock, WatchMetrics metrics) {
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
        metrics.registry().gauge("health.watch.online.count", this, DeviceRegistry::onlineCount);
    }

    /** 这块表的登记情况。 */
    public Lookup lookup(String imei) {
        ensureLoaded();
        String card = devices.get(imei);
        if (card == null) {
            return Lookup.UNREGISTERED;
        }
        return card.isEmpty() ? Lookup.UNBOUND : new Lookup(true, card);
    }

    /** 收到这块表的任何上行包。未登记的表不记（DEVICE 里没有它，也不算在线）。 */
    public void touch(String imei, long atMillis) {
        if (lookup(imei).registered()) {
            lastSeen.merge(imei, atMillis, Math::max);
            dirty.add(imei);
        }
    }

    /** 取出上次写库以来变化过的最后上行时间（秒以下舍去，和库里 TIMESTAMP(0) 一致）。 */
    public Map<String, LocalDateTime> drainDirtyLastSeen() {
        Map<String, LocalDateTime> out = new HashMap<>();
        for (String imei : List.copyOf(dirty)) {
            dirty.remove(imei);
            Long at = lastSeen.get(imei);
            if (at != null) {
                out.put(imei, toTime(at));
            }
        }
        return out;
    }

    /** 写库失败时放回去，下一轮再写。 */
    public void markDirty(Iterable<String> imeis) {
        imeis.forEach(dirty::add);
    }

    /** 最后上行时间在在线窗口内的已登记手表数。 */
    public int onlineCount() {
        long since = clock.millis() - TimeUnit.MINUTES.toMillis(props.onlineWindowMinutes());
        Map<String, String> current = devices;
        int n = 0;
        for (Map.Entry<String, Long> e : lastSeen.entrySet()) {
            if (e.getValue() >= since && current.containsKey(e.getKey())) {
                n++;
            }
        }
        return n;
    }

    @Scheduled(initialDelayString = "${hv2.watch.binding-refresh-seconds}",
            fixedDelayString = "${hv2.watch.binding-refresh-seconds}", timeUnit = TimeUnit.SECONDS)
    public void scheduledRefresh() {
        refresh();
    }

    /** 从 DEVICE 表重新读入登记和绑定；数据库暂时连不上时保留旧的。 */
    public synchronized void refresh() {
        try {
            Map<String, String> next = new HashMap<>();
            for (WatchMapper.DeviceRow row : mapper.selectEnabledDevices()) {
                next.put(row.imei(), row.cardCode() == null ? "" : row.cardCode());
                if (row.lastSeenAt() != null) {
                    long at = row.lastSeenAt().atZone(TimeConfig.ZONE).toInstant().toEpochMilli();
                    lastSeen.merge(row.imei(), at, Math::max);
                }
            }
            devices = Map.copyOf(next);
            lastSeen.keySet().retainAll(next.keySet());
            loaded = true;
        } catch (DataAccessException e) {
            log.warn("刷新手表绑定失败，继续用上次的绑定：{}", e.getMessage());
        }
    }

    /** 启动时数据库连不上：处理包时顺带重试，但最多 10 秒一次，免得每个包都卡在连库超时上。 */
    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        long now = clock.millis();
        if (now - lastLoadAttempt >= RETRY_MILLIS) {
            lastLoadAttempt = now;
            refresh();
        }
    }

    private static LocalDateTime toTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), TimeConfig.ZONE).truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * @param registered 已登记且启用
     * @param cardCode   绑定的卡编码；未绑定或未登记时为 null
     */
    public record Lookup(boolean registered, String cardCode) {
        static final Lookup UNREGISTERED = new Lookup(false, null);
        static final Lookup UNBOUND = new Lookup(true, null);

        public boolean bound() {
            return cardCode != null;
        }
    }
}
