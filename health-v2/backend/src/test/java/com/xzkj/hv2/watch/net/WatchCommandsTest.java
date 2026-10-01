package com.xzkj.hv2.watch.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** 下发与回复的格式（docs/03 第一节、第六节）。 */
class WatchCommandsTest {

    private static final String IMEI = "353456789012345";

    @Test
    void loginReplyIsUtcWithZone8() {
        // 北京时间 2026-09-24 17:30:00 = UTC 09:30:00
        assertThat(WatchCommands.loginReply(Instant.parse("2026-09-24T09:30:00Z")))
                .isEqualTo("IWBP00,20260924093000,8#");
    }

    @Test
    void activeReportsGetRepliesButAcknowledgementsDoNot() {
        assertThat(WatchCommands.replyFor("AP03")).isEqualTo("IWBP03#");
        assertThat(WatchCommands.replyFor("AP49")).isEqualTo("IWBP49#");
        assertThat(WatchCommands.replyFor("AP50")).isEqualTo("IWBP50#");
        assertThat(WatchCommands.replyFor("APHT")).isEqualTo("IWBPHT#");
        assertThat(WatchCommands.replyFor("APHP")).isEqualTo("IWBPHP#");
        assertThat(WatchCommands.replyFor("AP10")).isEqualTo("IWBP10#");
        for (String ack : new String[] {"APXL", "APXY", "APXZ", "APXT", "AP33", "AP86", "AP87", "AP07", "APZZ"}) {
            assertThat(WatchCommands.replyFor(ack)).as(ack).isNull();
        }
    }

    @Test
    void loginConfigurationTurnsOffWatchOwnSchedules() {
        String[] cmds = WatchCommands.loginConfiguration(IMEI);
        assertThat(cmds[0]).matches("IWBP33," + IMEI + ",\\d{6},1#");
        assertThat(cmds[1]).matches("IWBP86," + IMEI + ",\\d{6},0,1#");
        assertThat(cmds[2]).matches("IWBP87," + IMEI + ",\\d{6},0,1#");
    }

    @Test
    void measurementsRotate() {
        assertThat(WatchCommands.measurement(IMEI, 0)).matches("IWBPXL," + IMEI + ",\\d{6}#");
        assertThat(WatchCommands.measurement(IMEI, 1)).startsWith("IWBPXY,");
        assertThat(WatchCommands.measurement(IMEI, 2)).startsWith("IWBPXZ,");
        assertThat(WatchCommands.measurement(IMEI, 3)).startsWith("IWBPXT,");
        assertThat(WatchCommands.measurement(IMEI, 4)).startsWith("IWBPXL,");
    }
}
