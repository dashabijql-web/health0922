package com.xzkj.hv2.watch.net;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务器发给手表的包（docs/03 第一节、第六节）。
 */
public final class WatchCommands {

    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    /** 手表主动上报、服务器要回复的包 → 回复内容。不在表里的（指令确认、语音、未知）不回复。 */
    private static final Map<String, String> REPLIES = Map.of(
            "AP03", "IWBP03#",
            "AP49", "IWBP49#",
            "AP50", "IWBP50#",
            "APHT", "IWBPHT#",
            "APHP", "IWBPHP#",
            // 报警包：不需要回复地址，地址内容留空
            "AP10", "IWBP10#",
            "AP01", "IWBP01#",
            "AP02", "IWBP02#");

    /** 测量指令按这个顺序轮换：心率 → 血压 → 血氧 → 体温 */
    private static final String[] MEASUREMENTS = {"BPXL", "BPXY", "BPXZ", "BPXT"};

    private static final AtomicInteger SERIAL = new AtomicInteger();

    private WatchCommands() {
    }

    /** 这个上行包的回复；不需要回复时为 null。 */
    public static String replyFor(String code) {
        return REPLIES.get(code);
    }

    /** 登录回复带授时：UTC 零时区的 yyyyMMddHHmmss，8 是东八区。 */
    public static String loginReply(Instant now) {
        return "IWBP00," + UTC_TIME.format(now) + ",8#";
    }

    /**
     * 登录后的配置：工作模式设为正常（1）；关闭手表自己的心率/血压、体温测量周期，由服务器统一下发测量，
     * 避免两套调度重叠。BP86/BP87 的参数沿用老项目在真实手表上验证过确认包的写法。
     */
    public static String[] loginConfiguration(String imei) {
        return new String[] {
                "IWBP33," + imei + "," + nextSerial() + ",1#",
                "IWBP86," + imei + "," + nextSerial() + ",0,1#",
                "IWBP87," + imei + "," + nextSerial() + ",0,1#"};
    }

    /** 第 index 次测量指令（从 0 数起），按心率、血压、血氧、体温轮换。 */
    public static String measurement(String imei, int index) {
        return "IW" + MEASUREMENTS[Math.floorMod(index, MEASUREMENTS.length)] + "," + imei + "," + nextSerial() + "#";
    }

    /** 6 位指令流水号，到 999999 后从 1 重新开始。 */
    static String nextSerial() {
        int n = SERIAL.updateAndGet(v -> v >= 999_999 ? 1 : v + 1);
        return String.format("%06d", n);
    }
}
