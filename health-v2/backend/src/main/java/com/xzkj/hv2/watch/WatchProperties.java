package com.xzkj.hv2.watch;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 手表接入的配置（docs/01 第五节配置表，docs/03）。
 *
 * @param tcpEnabled               是否监听手表 TCP 端口（部分测试关掉）
 * @param tcpPort                  手表 TCP 端口
 * @param maxConnections           最多同时多少个连接，超过的新连接直接关闭
 * @param onlineWindowMinutes      最后上行时间在这个窗口内算"在线"
 * @param vitalStaleMinutes        单项体征超过它算"数据较旧"（页面用）
 * @param monitoringRefreshSeconds 每隔多少秒给每块表下发一项测量
 * @param loginTimeout             连上后多久不发 AP00 就关闭
 * @param idleTimeout              多久没有任何数据就关闭
 * @param maxFrameBytes            单包上限，超过丢弃并断开
 * @param bindingRefreshSeconds    "IMEI → 卡编码"绑定缓存多久从 DEVICE 表刷新一次
 * @param workerThreads            处理线程数（线程名前缀 watch-data-）
 * @param workerQueue              处理队列长度；满了由提交者（Netty 线程）自己执行，起回压作用
 * @param buffer                   Redis 缓冲与批量写库
 */
@ConfigurationProperties("hv2.watch")
public record WatchProperties(
        boolean tcpEnabled,
        int tcpPort,
        int maxConnections,
        int onlineWindowMinutes,
        int vitalStaleMinutes,
        int monitoringRefreshSeconds,
        Duration loginTimeout,
        Duration idleTimeout,
        int maxFrameBytes,
        int bindingRefreshSeconds,
        int workerThreads,
        int workerQueue,
        Buffer buffer) {

    /**
     * @param flushEnabled         是否定时写库（压测、演练时可关掉，让数据先积在缓冲里）
     * @param keyPrefix            Redis key 前缀，必须以 hv2: 开头
     * @param flushMillis          每隔多少毫秒写一轮
     * @param batchSize            一批最多多少条
     * @param memoryCapacity       Redis 不可用时内存队列的上限，满了丢最旧的
     * @param singleRetries        拆到单条后还写不进，重试几次再进死信
     * @param shutdownDrainTimeout 正常停止时最多花多久把缓冲写完
     */
    public record Buffer(
            boolean flushEnabled,
            String keyPrefix,
            long flushMillis,
            int batchSize,
            int memoryCapacity,
            int singleRetries,
            Duration shutdownDrainTimeout) {

        public Buffer {
            if (keyPrefix == null || !keyPrefix.startsWith("hv2:")) {
                throw new IllegalArgumentException("hv2.watch.buffer.key-prefix 必须以 hv2: 开头");
            }
        }
    }
}
