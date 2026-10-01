package com.xzkj.hv2.watch.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import com.xzkj.hv2.watch.WatchMetrics;

/** 切包（docs/03 第二节）：半包、粘包、AP07 里带 #、超长。 */
class WatchFrameDecoderTest {

    private SimpleMeterRegistry meters;
    private EmbeddedChannel ch;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        ch = new EmbeddedChannel(new WatchFrameDecoder(8192, new WatchMetrics(meters)));
    }

    private void send(String s) {
        send(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private void send(byte[] b) {
        ch.writeInbound(Unpooled.wrappedBuffer(b));
    }

    private WatchPacket next() {
        return ch.readInbound();
    }

    @Test
    void halfPacketWaitsForTheRest() {
        send("IWAP4");
        assertThat((Object) next()).isNull();
        send("9,68#");
        assertThat(next()).isEqualTo(new WatchPacket("AP49", "68"));
    }

    @Test
    void severalPacketsInOneRead() {
        send("IWAP49,68#IWAP50,36.7,90#IWAPHT,60,1");
        assertThat(next()).isEqualTo(new WatchPacket("AP49", "68"));
        assertThat(next()).isEqualTo(new WatchPacket("AP50", "36.7,90"));
        assertThat((Object) next()).isNull();
        send("30,85#");
        assertThat(next()).isEqualTo(new WatchPacket("APHT", "60,130,85"));
    }

    @Test
    void loginAndAlarmHaveNoCommaAfterCode() {
        send("IWAP00353456789012345#");
        assertThat(next()).isEqualTo(new WatchPacket("AP00", "353456789012345"));
        send("IWAP10080524A2232.9806N11404.9355E000.1061830323.8706000908000502,460,0,9520,3671,01,zh-cn,00,HOME|74-DE-2B-44-88-8C|97#");
        WatchPacket alarm = next();
        assertThat(alarm.code()).isEqualTo("AP10");
        assertThat(alarm.params()[5]).isEqualTo("01");
    }

    @Test
    void emptyApHpFieldsAreKept() {
        send("IWAPHP,,,,96,,,,,,,,,#");
        assertThat(next().params()).hasSize(13).startsWith("", "", "", "96");
    }

    @Test
    void voicePacketWithHashInsideAudioIsSkippedWhole() {
        byte[] audio = new byte[40];
        for (int i = 0; i < audio.length; i++) {
            audio[i] = (byte) (i % 3 == 0 ? '#' : i);
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes("IWAP07,20140818064408,6,1,40,".getBytes(StandardCharsets.US_ASCII));
        b.writeBytes(audio);
        b.writeBytes("#IWAP49,70#".getBytes(StandardCharsets.US_ASCII));
        byte[] all = b.toByteArray();
        // 分两次到达，第一次在音频中间断开
        send(java.util.Arrays.copyOfRange(all, 0, 45));
        assertThat((Object) next()).isNull();
        send(java.util.Arrays.copyOfRange(all, 45, all.length));
        assertThat(next().code()).isEqualTo("AP07");
        assertThat(next()).isEqualTo(new WatchPacket("AP49", "70"));
        assertThat(ch.isActive()).isTrue();
    }

    @Test
    void oversizeWithoutHashClosesConnection() {
        send("IWAP49," + "1".repeat(9000));
        assertThat(ch.isActive()).isFalse();
        assertThat(meters.counter("health.watch.dropped", "reason", "oversize").count()).isEqualTo(1);
    }

    @Test
    void malformedPacketIsDroppedButConnectionStays() {
        send("hello#IWAP49,72#");
        assertThat(next()).isEqualTo(new WatchPacket("AP49", "72"));
        assertThat(ch.isActive()).isTrue();
        assertThat(meters.counter("health.watch.dropped", "reason", "malformed").count()).isEqualTo(1);
    }
}
