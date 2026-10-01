package com.xzkj.hv2.watch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import com.xzkj.hv2.watch.buffer.BufferFlusher;
import com.xzkj.hv2.watch.net.WatchChannelHandler;
import com.xzkj.hv2.watch.net.WatchFrameDecoder;
import com.xzkj.hv2.watch.net.WatchSessions;

/**
 * 从收包到入库走一遍（docs/03）：连接用 Netty 的 EmbeddedChannel 代替真实 TCP，其余都是真的——
 * 处理线程池、绑定缓存、Redis 缓冲、批量写库、预警。
 */
class WatchPipelineTest extends WatchDbTestBase {

    private static final String BOUND = "869900000000001";
    private static final String UNBOUND = "869900000000002";
    private static final String UNKNOWN = "869900000000099";
    private static final String A = "62082300920390001";
    private static final String B = "62082300920390002";

    @Autowired
    private WatchSessions sessions;
    @Autowired
    private WatchPacketProcessor processor;
    @Autowired
    private WatchMetrics metrics;
    @Autowired
    private WatchProperties props;
    @Autowired
    private BufferFlusher flusher;

    @BeforeEach
    void watches() {
        assumeRedis();
        person(A, null);
        person(B, null);
        device(BOUND, A);
        device(UNBOUND, null);
    }

    private EmbeddedChannel connect() {
        EmbeddedChannel ch = new EmbeddedChannel(new WatchFrameDecoder(props.maxFrameBytes(), metrics),
                new WatchChannelHandler(sessions, processor, metrics, props, clock));
        ch.freezeTime();
        return ch;
    }

    private static void send(EmbeddedChannel ch, String packets) {
        ch.writeInbound(Unpooled.copiedBuffer(packets, StandardCharsets.ISO_8859_1));
    }

    private static List<String> replies(EmbeddedChannel ch) {
        List<String> out = new ArrayList<>();
        ByteBuf b;
        while ((b = ch.readOutbound()) != null) {
            out.add(b.toString(StandardCharsets.US_ASCII));
            b.release();
        }
        return out;
    }

    private EmbeddedChannel login(String imei) {
        EmbeddedChannel ch = connect();
        send(ch, "IWAP00" + imei + "#");
        replies(ch);
        return ch;
    }

    /** 等线程池把已收到的包处理完。 */
    private void settle() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (processor.pending() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    /** 等处理完，再写一轮库。 */
    private void flush() {
        settle();
        assertThat(flusher.flushRound()).isTrue();
    }

    @Test
    void loginSequence() {
        EmbeddedChannel ch = connect();
        send(ch, "IWAP00" + BOUND + "#");
        ch.runScheduledPendingTasks();
        List<String> first = replies(ch);
        assertThat(first.get(0)).isEqualTo("IWBP00,20260923020000,8#");
        assertThat(first.get(1)).matches("IWBP33," + BOUND + ",\\d{6},1#");

        ch.advanceTimeBy(3, TimeUnit.SECONDS);
        ch.runScheduledPendingTasks();
        List<String> next = replies(ch);
        assertThat(next).hasSize(3);
        assertThat(next.get(0)).startsWith("IWBP86,");
        assertThat(next.get(1)).startsWith("IWBP87,");
        assertThat(next.get(2)).startsWith("IWBPXL,");

        List<String> rotation = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ch.advanceTimeBy(props.monitoringRefreshSeconds(), TimeUnit.SECONDS);
            ch.runScheduledPendingTasks();
            replies(ch).forEach(r -> rotation.add(r.substring(0, 6)));
        }
        assertThat(rotation).containsExactly("IWBPXY", "IWBPXZ", "IWBPXT", "IWBPXL");
    }

    @Test
    void packetBeforeLoginClosesConnection() {
        EmbeddedChannel ch = connect();
        send(ch, "IWAP49,72#");
        assertThat(replies(ch)).isEmpty();
        assertThat(ch.isActive()).isFalse();
    }

    @Test
    void noLoginWithinThirtySecondsClosesConnection() {
        EmbeddedChannel ch = connect();
        ch.advanceTimeBy(props.loginTimeout().toSeconds() + 1, TimeUnit.SECONDS);
        ch.runScheduledPendingTasks();
        assertThat(ch.isActive()).isFalse();
    }

    @Test
    void acknowledgementsAreNotRepliedButReportsAre() {
        EmbeddedChannel ch = login(BOUND);
        send(ch, "IWAPXL,080835#IWAPXY,080836#IWAPXZ,080837#IWAPXT,080838#IWAP33,080839,1#IWAP86,080840#");
        assertThat(replies(ch)).as("指令确认不回复").isEmpty();
        send(ch, "IWAP49,72#IWAP03,06000908000102,05555,30#IWAPHP,,,,97,,,,,,,,,#");
        assertThat(replies(ch)).containsExactly("IWBP49#", "IWBP03#", "IWBPHP#");
    }

    @Test
    void vitalsOfBoundWatchAreStoredWithCardCodeAndLatestValues() {
        EmbeddedChannel ch = login(BOUND);
        send(ch, "IWAP49,72#IWAPHT,80,130,85#IWAPHP,,,,97,,,,,,,,,#IWAP50,36.6,0#");
        flush();
        clock.advance(Duration.ofMinutes(1));
        send(ch, "IWAP49,75#");
        flush();

        assertThat(count("SELECT COUNT(*) FROM HEALTH_RECORD WHERE CARD_CODE = ? AND DEVICE_IMEI = ?", A, BOUND))
                .isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM HEALTH_LATEST WHERE CARD_CODE = ?", A)).as("每人每指标一行").isEqualTo(4);
        Map<String, Object> hr = jdbc.queryForMap(
                "SELECT VAL1, COLLECTED_AT FROM HEALTH_LATEST WHERE CARD_CODE = ? AND METRIC = 'HEART_RATE'", A);
        assertThat((BigDecimal) hr.get("VAL1")).isEqualByComparingTo("75");
        assertThat(hr.get("COLLECTED_AT").toString()).startsWith("2026-09-23 10:01:00");
        Map<String, Object> bp = jdbc.queryForMap(
                "SELECT VAL1, VAL2 FROM HEALTH_LATEST WHERE CARD_CODE = ? AND METRIC = 'BLOOD_PRESSURE'", A);
        assertThat((BigDecimal) bp.get("VAL2")).isEqualByComparingTo("85");
        assertThat(count("SELECT SUM(TOTAL_COUNT) FROM METRIC_COUNTER")).isEqualTo(6);
        assertThat(count("SELECT TOTAL_COUNT FROM METRIC_COUNTER WHERE METRIC = 'HEART_RATE'")).isEqualTo(3);
    }

    @Test
    void unwornZerosAreDroppedAndCounted() {
        EmbeddedChannel ch = login(BOUND);
        double zero = dropped("zero");
        double placeholder = dropped("placeholder");
        send(ch, "IWAP49,0#IWAPHT,0,0,0#IWAPHP,0,0,0,95,0.0,0.0#IWAP50,0.0,80#");
        flush();
        assertThat(count("SELECT COUNT(*) FROM HEALTH_RECORD")).isZero();
        assertThat(dropped("zero") - zero).isEqualTo(4);
        assertThat(dropped("placeholder") - placeholder).isEqualTo(1);
    }

    @Test
    void unboundWatchKeepsOnlyBatteryAndStillRaisesSos() {
        EmbeddedChannel ch = login(UNBOUND);
        double unbound = dropped("unbound");
        send(ch, "IWAP49,130#IWAP03,06000907500102,01234,30#"
                + "IWAP10080524A2232.9806N11404.9355E000.1061830323.8706000908000502,460,0,9520,3671,01,zh-cn,00,#");
        flush();
        assertThat(count("SELECT COUNT(*) FROM HEALTH_RECORD")).isZero();
        assertThat(dropped("unbound") - unbound).isEqualTo(1);
        assertThat(count("SELECT BATTERY_PCT FROM DEVICE WHERE IMEI = ?", UNBOUND)).isEqualTo(75);
        assertThat(count("SELECT COUNT(*) FROM STEP_DAILY")).as("没绑定不知道是谁的步数").isZero();
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT WHERE CATEGORY = 'SOS' AND CARD_CODE IS NULL AND DEVICE_IMEI = ?",
                UNBOUND)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT WHERE CATEGORY = 'HEART_RATE'")).as("未绑定不判断越界").isZero();
    }

    @Test
    void unregisteredWatchGetsLoginReplyButNothingIsStoredExceptAlarm() {
        EmbeddedChannel ch = connect();
        send(ch, "IWAP00" + UNKNOWN + "#");
        assertThat(replies(ch).getFirst()).startsWith("IWBP00,");
        double unregistered = dropped("unregistered");
        send(ch, "IWAP49,80#IWAP10080524A2232.9806N11404.9355E000.1061830323.8706000908000502,460,0,9520,3671,05,zh-cn,00,#");
        flush();
        assertThat(count("SELECT COUNT(*) FROM HEALTH_RECORD")).isZero();
        assertThat(dropped("unregistered") - unregistered).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT WHERE CATEGORY = 'FALL' AND DEVICE_IMEI = ?", UNKNOWN))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM DEVICE WHERE IMEI = ?", UNKNOWN)).isZero();
    }

    @Test
    void highHeartRateRaisesEventImmediately() {
        EmbeddedChannel ch = login(BOUND);
        send(ch, "IWAP49,125#");
        flush();
        assertThat(count("SELECT COUNT(*) FROM ALERT_EVENT WHERE CARD_CODE = ? AND CODE = 'HR_HIGH'", A)).isEqualTo(1);
    }

    @Test
    void rebindingAnOnlineWatchTakesEffectWithoutReconnect() {
        EmbeddedChannel ch = login(BOUND);
        send(ch, "IWAP49,70#");
        flush();
        jdbc.update("UPDATE DEVICE SET CARD_CODE = ? WHERE IMEI = ?", B, BOUND);
        // 生产环境是每 60 秒刷新一次绑定缓存（后台改绑定时立即刷新），这里直接刷新
        devices.refresh();
        clock.advance(Duration.ofMinutes(1));
        send(ch, "IWAP49,71#");
        flush();
        assertThat(ch.isActive()).isTrue();
        assertThat(jdbc.queryForList("SELECT CARD_CODE FROM HEALTH_RECORD ORDER BY COLLECTED_AT", String.class))
                .containsExactly(A, B);
    }

    @Test
    void heartbeatWritesBatteryStepsAndLastSeen() {
        EmbeddedChannel ch = login(BOUND);
        // 真实手表心跳间隔几分钟，不会并发；这里每次等处理完再发下一个
        send(ch, "IWAP03,06000908000102,05000,30#");
        settle();
        clock.advance(Duration.ofMinutes(5));
        send(ch, "IWAP03,06000907900102,05120,30#");
        settle();
        clock.advance(Duration.ofMinutes(5));
        // 电量 0 是错报：不覆盖已有的有效值
        send(ch, "IWAP03,06000900000102,05200,30#");
        flush();
        Map<String, Object> dev = jdbc.queryForMap("SELECT * FROM DEVICE WHERE IMEI = ?", BOUND);
        assertThat(((Number) dev.get("BATTERY_PCT")).intValue()).isEqualTo(79);
        assertThat(dev.get("LAST_SEEN_AT").toString()).startsWith("2026-09-23 10:10:00");
        Map<String, Object> steps = jdbc.queryForMap("SELECT * FROM STEP_DAILY WHERE CARD_CODE = ?", A);
        assertThat(((Number) steps.get("STEPS")).intValue()).isEqualTo(200);
        assertThat(((Number) steps.get("LAST_RAW")).intValue()).isEqualTo(5200);
        assertThat(devices.onlineCount()).isEqualTo(1);
    }

    @Test
    void sameImeiLoggingInAgainReplacesTheOldConnection() {
        EmbeddedChannel first = login(BOUND);
        EmbeddedChannel second = login(BOUND);
        assertThat(first.isActive()).isFalse();
        assertThat(second.isActive()).isTrue();
    }
}
