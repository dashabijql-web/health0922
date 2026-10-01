package com.xzkj.hv2.watch.net;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.ScheduledFuture;

import com.xzkj.hv2.watch.WatchMetrics;
import com.xzkj.hv2.watch.WatchMetrics.DropReason;
import com.xzkj.hv2.watch.WatchPacketProcessor;
import com.xzkj.hv2.watch.WatchProperties;

/**
 * 一个手表连接（docs/03 第二节、第六节）。只在 Netty 线程里做收发：
 * 登录、回复、下发测量指令；数据处理（有效性、绑定、缓冲、预警）交给 {@link WatchPacketProcessor} 的线程池。
 * <ul>
 *   <li>没登录就发其他包：丢弃并关闭连接；连上后 30 秒内不发 AP00 也关闭；</li>
 *   <li>登录后依次下发 BP33、BP86、BP87，然后每隔 WATCH_MONITORING_REFRESH_SECONDS 秒下发一项测量；</li>
 *   <li>手表主动上报的包立即回复；指令确认（APXL 等）不回复，否则会把测量指令又发回去。</li>
 * </ul>
 * 每个连接一个实例。
 */
public class WatchChannelHandler extends SimpleChannelInboundHandler<WatchPacket> {

    private static final Logger log = LoggerFactory.getLogger(WatchChannelHandler.class);
    private static final Pattern IMEI = Pattern.compile("\\d{15}");
    /** 登录后几条配置指令之间、配置指令和第一条测量指令之间的间隔 */
    static final long COMMAND_GAP_MILLIS = 1000;

    private final WatchSessions sessions;
    private final WatchPacketProcessor processor;
    private final WatchMetrics metrics;
    private final WatchProperties props;
    private final Clock clock;

    private String imei;
    private boolean opened;
    private ScheduledFuture<?> loginTimeout;
    private ScheduledFuture<?> measureLoop;
    private int measureIndex;

    public WatchChannelHandler(WatchSessions sessions, WatchPacketProcessor processor, WatchMetrics metrics,
                               WatchProperties props, Clock clock) {
        this.sessions = sessions;
        this.processor = processor;
        this.metrics = metrics;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (!sessions.tryOpen()) {
            metrics.dropped(DropReason.CONNECTION_LIMIT);
            log.warn("手表连接数已达上限 {}，关闭新连接 {}", props.maxConnections(), ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        opened = true;
        loginTimeout = ctx.executor().schedule(() -> {
            if (imei == null) {
                log.info("连接 {} 在 {} 内没有登录，关闭", ctx.channel().remoteAddress(), props.loginTimeout());
                ctx.close();
            }
        }, props.loginTimeout().toMillis(), TimeUnit.MILLISECONDS);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancel(loginTimeout);
        cancel(measureLoop);
        if (opened) {
            sessions.closed(imei, ctx.channel());
        }
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WatchPacket packet) {
        metrics.packet(packet.code());
        long receivedAt = clock.millis();
        if ("AP00".equals(packet.code())) {
            login(ctx, packet.body(), receivedAt);
            return;
        }
        if (imei == null) {
            metrics.dropped(DropReason.NOT_LOGGED_IN);
            log.info("连接 {} 没登录就发 {}，关闭", ctx.channel().remoteAddress(), packet.code());
            ctx.close();
            return;
        }
        String reply = WatchCommands.replyFor(packet.code());
        if (reply != null) {
            write(ctx, reply);
        }
        processor.submit(imei, packet, receivedAt);
    }

    private void login(ChannelHandlerContext ctx, String body, long receivedAt) {
        String id = body.strip();
        if (!IMEI.matcher(id).matches()) {
            metrics.dropped(DropReason.MALFORMED);
            log.info("连接 {} 的登录包 IMEI 格式不对，关闭", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        boolean first = imei == null;
        if (!first && !id.equals(imei)) {
            log.info("连接 {} 先后用 {} 和 {} 登录，关闭", ctx.channel().remoteAddress(), imei, id);
            ctx.close();
            return;
        }
        if (first) {
            imei = id;
            sessions.login(id, ctx.channel());
        }
        cancel(loginTimeout);
        write(ctx, WatchCommands.loginReply(clock.instant()));
        processor.onLogin(id, receivedAt);
        if (first) {
            startCommands(ctx);
        }
    }

    /** 登录后：先发配置指令，再按固定节奏轮换下发测量指令。 */
    private void startCommands(ChannelHandlerContext ctx) {
        String id = imei;
        String[] config = WatchCommands.loginConfiguration(id);
        for (int i = 0; i < config.length; i++) {
            String cmd = config[i];
            ctx.executor().schedule(() -> write(ctx, cmd), i * COMMAND_GAP_MILLIS, TimeUnit.MILLISECONDS);
        }
        long first = config.length * COMMAND_GAP_MILLIS;
        long period = TimeUnit.SECONDS.toMillis(props.monitoringRefreshSeconds());
        measureLoop = ctx.executor().scheduleAtFixedRate(
                () -> write(ctx, WatchCommands.measurement(id, measureIndex++)), first, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.info("手表 {} 超过 {} 没有任何数据，关闭连接", imei, props.idleTimeout());
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("手表 {} 连接异常：{}", imei, cause.toString());
        ctx.close();
    }

    private static void write(ChannelHandlerContext ctx, String text) {
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(Unpooled.copiedBuffer(text, StandardCharsets.US_ASCII));
        }
    }

    private static void cancel(ScheduledFuture<?> f) {
        if (f != null) {
            f.cancel(false);
        }
    }
}
