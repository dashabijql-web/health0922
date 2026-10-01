package com.xzkj.hv2.watch;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.xzkj.hv2.alert.AlertEngine;
import com.xzkj.hv2.watch.WatchMetrics.DropReason;
import com.xzkj.hv2.watch.buffer.MsgIds;
import com.xzkj.hv2.watch.buffer.WatchBuffer;
import com.xzkj.hv2.watch.buffer.WatchDatum;
import com.xzkj.hv2.watch.net.WatchPacket;

/**
 * 处理登录后的上行包（docs/03 第二、三、四节）。在有界线程池里执行（线程名前缀 watch-data-），
 * 队列满时由提交者（Netty 线程）自己执行，起回压作用；这里不直接写大批数据，体征、心跳进缓冲，只有预警事件直接写库。
 * <pre>
 * 手表                体征、心跳                         AP10 的 SOS、跌倒等
 * 已登记、已绑定       正常入库                           生成事件，带卡编码
 * 已登记、未绑定       体征不入库，只计数；心跳照常更新电量   照常生成事件，卡编码为空、记设备号
 * 未登记              数据不入库，只计数、限频记日志         同上
 * </pre>
 * 卡编码在处理时从绑定缓存里查，写进这条数据，之后不再改。
 */
@Component
public class WatchPacketProcessor {

    private static final Logger log = LoggerFactory.getLogger(WatchPacketProcessor.class);
    /** 未登记手表的日志每块表最多 10 分钟记一次 */
    private static final long UNREGISTERED_LOG_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private final DeviceRegistry devices;
    private final WatchBuffer buffer;
    private final StepCounter steps;
    private final AlertEngine alerts;
    private final MsgIds msgIds;
    private final WatchMetrics metrics;
    private final WatchProperties props;
    private volatile ThreadPoolExecutor executor;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Map<String, Long> unregisteredLogged = new ConcurrentHashMap<>();

    public WatchPacketProcessor(DeviceRegistry devices, WatchBuffer buffer, StepCounter steps, AlertEngine alerts,
                                MsgIds msgIds, WatchMetrics metrics, WatchProperties props) {
        this.devices = devices;
        this.buffer = buffer;
        this.steps = steps;
        this.alerts = alerts;
        this.msgIds = msgIds;
        this.metrics = metrics;
        this.props = props;
        start();
    }

    /** 建线程池；停止后可以再次启动（如测试框架暂停再恢复 Spring 容器）。 */
    public synchronized void start() {
        if (executor == null || executor.isShutdown()) {
            executor = new ThreadPoolExecutor(props.workerThreads(), props.workerThreads(), 60, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(props.workerQueue()), namedThreads("watch-data-"),
                    new ThreadPoolExecutor.CallerRunsPolicy());
        }
    }

    /** 交给线程池处理。receivedAt 是 Netty 线程收到的时间，就是这条数据的采集时间。 */
    public void submit(String imei, WatchPacket packet, long receivedAt) {
        run(() -> handle(imei, packet, receivedAt));
    }

    private void run(Runnable task) {
        inFlight.incrementAndGet();
        executor.execute(() -> {
            try {
                task.run();
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    /** 登录：刷新在线时间；未登记的表限频记一条日志。 */
    public void onLogin(String imei, long receivedAt) {
        run(() -> {
            devices.touch(imei, receivedAt);
            DeviceRegistry.Lookup who = devices.lookup(imei);
            if (!who.registered()) {
                logUnregistered(imei, receivedAt);
            } else if (!who.bound()) {
                log.info("手表 {} 登录，已登记但未绑定人员", imei);
            }
        });
    }

    /** 停止接收后调用：等队列里已收到的包处理完。 */
    public synchronized void shutdown(long timeoutMillis) throws InterruptedException {
        ThreadPoolExecutor e = executor;
        e.shutdown();
        if (!e.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
            log.warn("手表数据处理线程在 {} 毫秒内没有处理完，剩余 {} 个包", timeoutMillis, e.getQueue().size());
        }
    }

    void handle(String imei, WatchPacket packet, long at) {
        try {
            devices.touch(imei, at);
            DeviceRegistry.Lookup who = devices.lookup(imei);
            switch (packet.code()) {
                case "AP03" -> heartbeat(imei, who, packet.params(), at);
                case "AP49", "AP50", "APHT", "APHP" -> vitals(imei, who, packet, at);
                case "AP10" -> alarm(imei, who, packet.params(), at);
                case "APXL", "APXY", "APXZ", "APXT", "AP33", "AP86", "AP87" ->
                        log.debug("手表 {} 已确认指令 {}", imei, packet.code());
                default -> {
                    // 定位包、语音、其他：只计数（已在 Netty 线程里按协议号计过）
                }
            }
        } catch (RuntimeException e) {
            log.error("处理手表 {} 的 {} 包出错", imei, packet.code(), e);
        }
    }

    private void vitals(String imei, DeviceRegistry.Lookup who, WatchPacket packet, long at) {
        VitalParser.Result r = VitalParser.parse(packet.code(), packet.params());
        r.drops().forEach(metrics::dropped);
        if (r.readings().isEmpty()) {
            return;
        }
        if (!who.registered()) {
            metrics.dropped(DropReason.UNREGISTERED, r.readings().size());
            logUnregistered(imei, at);
            return;
        }
        if (!who.bound()) {
            // 体征表以卡编码为准，没绑定的表不知道是谁的，不入库、不判断越界
            metrics.dropped(DropReason.UNBOUND, r.readings().size());
            return;
        }
        for (VitalParser.Reading v : r.readings()) {
            String metric = v.metric().name();
            buffer.push(WatchDatum.vital(msgIds.next(at), imei, who.cardCode(), at, metric, v.val1(), v.val2()));
            alerts.checkVital(who.cardCode(), imei, metric, v.val1(), v.val2(), at);
        }
    }

    /** AP03,状态串,步数,翻滚次数：电量是状态串第 6–8 位。 */
    private void heartbeat(String imei, DeviceRegistry.Lookup who, String[] p, long at) {
        if (!who.registered()) {
            metrics.dropped(DropReason.UNREGISTERED);
            logUnregistered(imei, at);
            return;
        }
        Integer battery = p.length > 0 && p[0].length() >= 9 ? VitalParser.battery(p[0].substring(6, 9)) : null;
        Long raw = p.length > 1 ? parseSteps(p[1]) : null;
        Long today = null;
        if (who.bound() && raw != null) {
            today = steps.count(imei, who.cardCode(), raw, at);
        }
        if (battery == null && today == null) {
            return;
        }
        buffer.push(WatchDatum.heartbeat(msgIds.next(at), imei, who.cardCode(), at, battery, today,
                today == null ? null : raw));
    }

    /** AP10：按逗号拆开后第 6 段（下标 5）是报警代码。未登记、未绑定的表也生成事件，按设备号区分。 */
    private void alarm(String imei, DeviceRegistry.Lookup who, String[] p, long at) {
        if (p.length <= 5) {
            metrics.dropped(DropReason.MALFORMED);
            return;
        }
        if (!alerts.deviceAlarm(who.cardCode(), imei, p[5], at)) {
            metrics.dropped(DropReason.UNKNOWN_ALARM);
            log.info("手表 {} 发来不认识的报警代码 {}", imei, p[5]);
        }
    }

    private void logUnregistered(String imei, long at) {
        Long last = unregisteredLogged.get(imei);
        if (last == null || at - last >= UNREGISTERED_LOG_MILLIS) {
            if (unregisteredLogged.size() > 10_000) {
                // 有人拿大量随机 IMEI 乱连时，别让这张表无限变大
                unregisteredLogged.clear();
            }
            unregisteredLogged.put(imei, at);
            log.info("未登记的手表 {} 在上传数据，数据不入库", imei);
        }
    }

    private static Long parseSteps(String s) {
        try {
            long v = Long.parseLong(s.strip());
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** 已提交、还没处理完的包数（测试里等处理完用）。 */
    long pending() {
        return inFlight.get();
    }
}
