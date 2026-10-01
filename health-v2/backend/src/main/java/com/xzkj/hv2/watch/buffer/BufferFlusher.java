package com.xzkj.hv2.watch.buffer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import com.xzkj.hv2.common.db.DbErrors;
import com.xzkj.hv2.watch.DeviceRegistry;
import com.xzkj.hv2.watch.WatchMetrics;
import com.xzkj.hv2.watch.WatchProperties;

/**
 * 每 5 秒一轮，把缓冲成批写进库（docs/03 第五节）。
 * <ol>
 *   <li>把内存里变化过的最后上行时间写进 DEVICE；</li>
 *   <li>内存队列（Redis 不可用时的降级队列）里有数据，先写完它；写完且 Redis 能用了，切回 Redis；</li>
 *   <li>先处理 processing 里上次留下的一批（进程崩溃、写库失败时留下的），再从 buf 移一批写一批，
 *       一批满了接着移下一批，直到 buf 移空。</li>
 * </ol>
 * 写库失败时：数据库连不上（暂时性错误）就整批留着，下一轮重试；是数据本身的错，就把这一批对半拆开分别写，
 * 还失败的继续拆，直到找出写不进去的单条，单条重试 3 次仍失败移进死信，其余照常入库。
 * 同一时刻只有一轮在跑（定时任务和停止时的收尾共用一把锁）。
 */
@Component
public class BufferFlusher {

    private static final Logger log = LoggerFactory.getLogger(BufferFlusher.class);

    private final WatchBuffer buffer;
    private final WatchBatchWriter writer;
    private final DeviceRegistry devices;
    private final WatchProperties.Buffer props;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong backlog = new AtomicLong();
    private final Timer flushTimer;
    private final Counter splitCounter;
    private final Counter deadCounter;

    public BufferFlusher(WatchBuffer buffer, WatchBatchWriter writer, DeviceRegistry devices, WatchProperties props,
                         WatchMetrics metrics) {
        this.buffer = buffer;
        this.writer = writer;
        this.devices = devices;
        this.props = props.buffer();
        this.flushTimer = Timer.builder("health.buffer.flush.duration").description("每批写库耗时")
                .register(metrics.registry());
        this.splitCounter = metrics.registry().counter("health.buffer.split");
        this.deadCounter = metrics.registry().counter("health.buffer.dead");
        metrics.registry().gauge("health.buffer.size", backlog);
    }

    @Scheduled(initialDelayString = "${hv2.watch.buffer.flush-millis}",
            fixedDelayString = "${hv2.watch.buffer.flush-millis}", timeUnit = TimeUnit.MILLISECONDS)
    public void scheduled() {
        if (props.flushEnabled()) {
            flushRound();
        }
    }

    /**
     * 写一轮。
     *
     * @return 这一轮结束时缓冲是否已经写空（数据库或 Redis 出错、另一轮正在跑时为 false）
     */
    public boolean flushRound() {
        if (!lock.tryLock()) {
            return false;
        }
        try {
            writeLastSeen();
            if (!drainMemory()) {
                return false;
            }
            if (!buffer.redisMode()) {
                if (!buffer.redisReachable()) {
                    return true;
                }
                buffer.useRedis();
            }
            return drainRedis();
        } catch (DataAccessException e) {
            // 走到这里的是 Redis 的错误（数据库的错误在 writeSplit 里处理），数据还在 Redis 里，下一轮再来
            log.warn("本轮写库中断（Redis 不可用）：{}", e.getMessage());
            return false;
        } finally {
            updateBacklog();
            lock.unlock();
        }
    }

    /** 正常停止时调用：一轮接一轮写，直到缓冲写空或超时。 */
    public boolean drainAll(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (flushRound()) {
                return true;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public long backlog() {
        return backlog.get();
    }

    private void writeLastSeen() {
        Map<String, LocalDateTime> seen = devices.drainDirtyLastSeen();
        if (seen.isEmpty()) {
            return;
        }
        try {
            writer.updateLastSeen(seen);
        } catch (DataAccessException e) {
            devices.markDirty(seen.keySet());
            log.warn("写手表最后在线时间失败，下一轮重试：{}", e.getMessage());
        }
    }

    private boolean drainMemory() {
        while (true) {
            List<WatchDatum> batch = buffer.pollMemory(props.batchSize());
            if (batch.isEmpty()) {
                return true;
            }
            List<Item> items = batch.stream().map(d -> new Item(buffer.serialize(d), d)).toList();
            if (!writeSplit(items)) {
                buffer.returnToMemory(batch);
                return false;
            }
        }
    }

    private boolean drainRedis() {
        List<String> leftover = buffer.processing();
        if (!leftover.isEmpty()) {
            log.info("处理上次留下的一批 {} 条", leftover.size());
            if (!writeRaw(leftover)) {
                return false;
            }
            buffer.clearProcessing();
        }
        while (true) {
            List<String> batch = buffer.moveBatch(props.batchSize());
            if (batch.isEmpty()) {
                return true;
            }
            if (!writeRaw(batch)) {
                return false;
            }
            buffer.clearProcessing();
            if (batch.size() < props.batchSize()) {
                return true;
            }
        }
    }

    /** 解析不了的直接进死信，其余写库。 */
    private boolean writeRaw(List<String> raws) {
        List<Item> items = new ArrayList<>(raws.size());
        List<String> bad = new ArrayList<>();
        for (String raw : raws) {
            WatchDatum d = buffer.parse(raw);
            if (d == null || !wellFormed(d)) {
                bad.add(raw);
            } else {
                items.add(new Item(raw, d));
            }
        }
        if (!bad.isEmpty()) {
            log.error("{} 条缓冲数据格式不对，移进死信", bad.size());
            buffer.dead(bad);
            deadCounter.increment(bad.size());
        }
        return items.isEmpty() || writeSplit(items);
    }

    /** @return false 表示数据库暂时不可用，这一批要留到下一轮 */
    private boolean writeSplit(List<Item> items) {
        try {
            write(items);
            return true;
        } catch (RuntimeException e) {
            if (isDatabaseUnavailable(e)) {
                log.warn("数据库暂时不可用，{} 条留到下一轮：{}", items.size(), e.getMessage());
                return false;
            }
            if (!(e instanceof DataAccessException dataError)) {
                // 不是数据库报的错（程序问题），拆开也没用，整批留着等排查
                log.error("写库出现程序错误，{} 条留到下一轮", items.size(), e);
                return false;
            }
            if (items.size() == 1) {
                return retrySingle(items.getFirst(), dataError);
            }
            splitCounter.increment();
            int mid = items.size() / 2;
            return writeSplit(items.subList(0, mid)) && writeSplit(items.subList(mid, items.size()));
        }
    }

    private boolean retrySingle(Item item, DataAccessException first) {
        DataAccessException last = first;
        for (int i = 0; i < props.singleRetries(); i++) {
            try {
                write(List.of(item));
                return true;
            } catch (RuntimeException e) {
                if (isDatabaseUnavailable(e) || !(e instanceof DataAccessException dataError)) {
                    return false;
                }
                last = dataError;
            }
        }
        // 只记编号和数据库错误码，不记体征数值：Oracle 的错误信息里可能带着值（docs/01"安全"）
        log.error("MSG_ID {} 重试 {} 次仍写不进库，移进死信：{}", item.datum().msgId(), props.singleRetries(),
                DbErrors.code(last));
        buffer.dead(List.of(item.raw()));
        deadCounter.increment();
        return true;
    }

    private void write(List<Item> items) {
        List<WatchDatum> batch = items.stream().map(Item::datum).toList();
        flushTimer.record(() -> writer.write(batch));
    }

    private void updateBacklog() {
        long n = buffer.memorySize();
        if (buffer.redisMode()) {
            try {
                n += buffer.redisBacklog();
            } catch (DataAccessException e) {
                // Redis 不可用时只算内存队列
            }
        }
        backlog.set(n);
    }

    private static boolean wellFormed(WatchDatum d) {
        if (d.imei() == null || d.at() <= 0 || d.msgId() <= 0) {
            return false;
        }
        if (d.isVital()) {
            return d.card() != null && d.metric() != null && d.val1() != null;
        }
        return WatchDatum.HEARTBEAT.equals(d.type());
    }

    static boolean isDatabaseUnavailable(Throwable e) {
        return e instanceof TransientDataAccessException
                || e instanceof DataAccessResourceFailureException
                || e instanceof RecoverableDataAccessException
                || e instanceof CannotCreateTransactionException;
    }

    private record Item(String raw, WatchDatum datum) {
    }
}
