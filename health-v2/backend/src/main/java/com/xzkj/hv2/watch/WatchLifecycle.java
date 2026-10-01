package com.xzkj.hv2.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.xzkj.hv2.alert.AlertRules;
import com.xzkj.hv2.watch.buffer.BufferFlusher;
import com.xzkj.hv2.watch.net.WatchTcpServer;

/**
 * 手表链路的启动和停止顺序。
 * <pre>
 * 启动：读入绑定和阈值 → 监听 9001
 * 停止：关闭 9001（不再收新数据）→ 等线程池处理完已收到的包 → 把缓冲写完（最多 20 秒）
 * </pre>
 * 停止排在 Redis 连接和数据库连接池关闭之前，所以收尾时两者都还能用。
 */
@Component
public class WatchLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WatchLifecycle.class);
    private static final long PROCESSOR_DRAIN_MILLIS = 10_000;

    private final WatchProperties props;
    private final DeviceRegistry devices;
    private final AlertRules rules;
    private final WatchTcpServer server;
    private final WatchPacketProcessor processor;
    private final BufferFlusher flusher;
    private volatile boolean running;

    public WatchLifecycle(WatchProperties props, DeviceRegistry devices, AlertRules rules, WatchTcpServer server,
                          WatchPacketProcessor processor, BufferFlusher flusher) {
        this.props = props;
        this.devices = devices;
        this.rules = rules;
        this.server = server;
        this.processor = processor;
        this.flusher = flusher;
    }

    @Override
    public void start() {
        processor.start();
        devices.refresh();
        rules.refresh();
        if (props.tcpEnabled()) {
            try {
                server.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("手表 TCP 服务启动被中断", e);
            }
        }
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        server.stop();
        try {
            processor.shutdown(PROCESSOR_DRAIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!props.buffer().flushEnabled()) {
            log.info("缓冲写库已关闭（hv2.watch.buffer.flush-enabled=false），缓冲里的数据留在 Redis");
            return;
        }
        if (flusher.drainAll(props.buffer().shutdownDrainTimeout())) {
            log.info("停止前缓冲已全部写库");
        } else {
            log.warn("停止前缓冲没写完，剩余约 {} 条（Redis 里的数据下次启动继续写）", flusher.backlog());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 比 Web 服务器的优雅停机（MAX_VALUE - 1024）晚、比 Redis 连接和连接池早。 */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 2048;
    }
}
