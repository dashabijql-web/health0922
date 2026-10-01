package com.xzkj.hv2.watch.net;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.netty.channel.Channel;

import com.xzkj.hv2.watch.WatchMetrics;
import com.xzkj.hv2.watch.WatchProperties;

/**
 * 连接与手表的对应关系（docs/03 第二节）。只记"连接 → IMEI"；卡编码每个包处理时再查绑定缓存。
 * 同一 IMEI 再次登录，新连接替换旧连接，旧连接被关闭。
 */
@Component
public class WatchSessions {

    private static final Logger log = LoggerFactory.getLogger(WatchSessions.class);

    private final AtomicInteger connections = new AtomicInteger();
    private final Map<String, Channel> byImei = new ConcurrentHashMap<>();
    private final int maxConnections;

    public WatchSessions(WatchProperties props, WatchMetrics metrics) {
        this.maxConnections = props.maxConnections();
        metrics.registry().gauge("health.watch.connections", connections);
    }

    /** 新连接进来：没超过上限返回 true 并计数；超过返回 false，调用方关闭连接。 */
    boolean tryOpen() {
        while (true) {
            int n = connections.get();
            if (n >= maxConnections) {
                return false;
            }
            if (connections.compareAndSet(n, n + 1)) {
                return true;
            }
        }
    }

    void closed(String imei, Channel channel) {
        connections.decrementAndGet();
        if (imei != null) {
            byImei.remove(imei, channel);
        }
    }

    /** 登录：记下 IMEI 对应的连接，挤掉同一 IMEI 的旧连接。 */
    void login(String imei, Channel channel) {
        Channel old = byImei.put(imei, channel);
        if (old != null && old != channel) {
            log.info("手表 {} 从新连接登录，关闭旧连接 {}", imei, old.remoteAddress());
            old.close();
        }
    }

    public int connectionCount() {
        return connections.get();
    }

    public int loggedInCount() {
        return byImei.size();
    }
}
