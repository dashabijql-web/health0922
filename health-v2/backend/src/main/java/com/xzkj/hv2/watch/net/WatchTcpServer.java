package com.xzkj.hv2.watch.net;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import com.xzkj.hv2.watch.WatchMetrics;
import com.xzkj.hv2.watch.WatchPacketProcessor;
import com.xzkj.hv2.watch.WatchProperties;

/**
 * 手表 TCP 服务（默认端口 9001）。启动和停止由 {@link com.xzkj.hv2.watch.WatchLifecycle} 统一安排顺序。
 * <pre>
 * 每个连接的处理链：空闲检测（10 分钟没数据）→ 切包 → 会话与回复
 * </pre>
 */
@Component
public class WatchTcpServer {

    private static final Logger log = LoggerFactory.getLogger(WatchTcpServer.class);

    private final WatchProperties props;
    private final WatchSessions sessions;
    private final WatchPacketProcessor processor;
    private final WatchMetrics metrics;
    private final Clock clock;

    private EventLoopGroup boss;
    private EventLoopGroup workers;
    private Channel serverChannel;

    public WatchTcpServer(WatchProperties props, WatchSessions sessions, WatchPacketProcessor processor,
                          WatchMetrics metrics, Clock clock) {
        this.props = props;
        this.sessions = sessions;
        this.processor = processor;
        this.metrics = metrics;
        this.clock = clock;
    }

    public synchronized void start() throws InterruptedException {
        if (serverChannel != null) {
            return;
        }
        boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workers = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        long idleMillis = props.idleTimeout().toMillis();
        ServerBootstrap b = new ServerBootstrap()
                .group(boss, workers)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("idle", new IdleStateHandler(idleMillis, 0, 0, TimeUnit.MILLISECONDS))
                                .addLast("frame", new WatchFrameDecoder(props.maxFrameBytes(), metrics))
                                .addLast("session", new WatchChannelHandler(sessions, processor, metrics, props, clock));
                    }
                });
        serverChannel = b.bind(props.tcpPort()).sync().channel();
        log.info("手表 TCP 服务已监听端口 {}", props.tcpPort());
    }

    /** 停止接收：关闭监听和所有连接，等 Netty 线程退出。 */
    public synchronized void stop() {
        if (serverChannel == null) {
            return;
        }
        serverChannel.close().syncUninterruptibly();
        workers.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        boss.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        serverChannel = null;
        log.info("手表 TCP 服务已停止");
    }
}
