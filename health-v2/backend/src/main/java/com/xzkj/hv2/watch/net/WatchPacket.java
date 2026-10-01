package com.xzkj.hv2.watch.net;

import java.util.regex.Pattern;

/**
 * 一个完整的上行包：{@code IW} + 协议号（4 位）+ 参数 + {@code #}（docs/03 第一节）。
 *
 * @param code 协议号，如 AP49
 * @param body 参数部分（去掉协议号后面紧跟的逗号和结尾的 #）。AP00、AP10 的协议号后面没有逗号，直接接内容
 */
public record WatchPacket(String code, String body) {

    private static final Pattern CODE = Pattern.compile("[A-Z]{2}[A-Z0-9]{2}");

    /** 语音上行只计数、不解析，解码器整段读完后给出这个包。 */
    static final WatchPacket VOICE = new WatchPacket("AP07", "");

    /**
     * 解析一个以 # 结尾的包；格式不对返回 null。
     * 包前后的空白（有的设备会带换行）先去掉。
     */
    public static WatchPacket parse(String frame) {
        String f = frame.strip();
        if (f.length() < 7 || !f.startsWith("IW") || !f.endsWith("#")) {
            return null;
        }
        String code = f.substring(2, 6);
        if (!CODE.matcher(code).matches()) {
            return null;
        }
        String body = f.substring(6, f.length() - 1);
        if (body.startsWith(",")) {
            body = body.substring(1);
        }
        return new WatchPacket(code, body);
    }

    /** 按逗号拆开参数，保留空字段（APHP 没值的项留空）。 */
    public String[] params() {
        return body.isEmpty() ? new String[0] : body.split(",", -1);
    }
}
