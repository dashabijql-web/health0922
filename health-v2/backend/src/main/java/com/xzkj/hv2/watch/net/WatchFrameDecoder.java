package com.xzkj.hv2.watch.net;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import com.xzkj.hv2.watch.WatchMetrics;
import com.xzkj.hv2.watch.WatchMetrics.DropReason;

/**
 * 切包（docs/03 第二节）：TCP 是字节流，一次可能收到半个包或多个包。按 # 切开，不完整的留到下次拼接。
 * <ul>
 *   <li>单包超过上限（默认 8 KB）丢弃并断开连接；</li>
 *   <li>{@code AP07} 语音上行带二进制音频，里面可能出现 #：识别到 {@code IWAP07} 时，
 *       按它的长度字段（第 4 个逗号后的数字）整段读完再丢弃；</li>
 *   <li>格式不对的包丢弃、计数，不断开（手表偶尔发坏包不该被踢下线）。</li>
 * </ul>
 * 每个连接一个实例（内部有未拼完的数据），不能共享。
 */
public class WatchFrameDecoder extends ByteToMessageDecoder {

    private static final byte END = '#';
    private static final byte[] VOICE_HEAD = "IWAP07".getBytes(StandardCharsets.US_ASCII);
    /** AP07 头部 IWAP07,时间,总包数,第几包,长度, 不会超过这么长 */
    private static final int VOICE_HEADER_MAX = 64;
    private static final int VOICE_HEADER_COMMAS = 5;

    private final int maxFrameBytes;
    private final WatchMetrics metrics;

    public WatchFrameDecoder(int maxFrameBytes, WatchMetrics metrics) {
        this.maxFrameBytes = maxFrameBytes;
        this.metrics = metrics;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int start = in.readerIndex();
        int readable = in.readableBytes();

        if (startsWith(in, VOICE_HEAD)) {
            decodeVoice(ctx, in, out);
            return;
        }
        int end = in.indexOf(start, in.writerIndex(), END);
        if (end < 0) {
            // 还可能是 IWAP07 的开头，或者包还没到齐
            if (readable > maxFrameBytes) {
                tooLong(ctx, in);
            }
            return;
        }
        int length = end - start + 1;
        if (length > maxFrameBytes) {
            tooLong(ctx, in);
            return;
        }
        String frame = in.readCharSequence(length, StandardCharsets.ISO_8859_1).toString();
        WatchPacket packet = WatchPacket.parse(frame);
        if (packet == null) {
            metrics.dropped(DropReason.MALFORMED);
            return;
        }
        out.add(packet);
    }

    /** IWAP07,时间,总包数,第几包,长度,<音频字节>#：头部读全后按长度跳过整段。 */
    private void decodeVoice(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int start = in.readerIndex();
        int limit = Math.min(in.writerIndex(), start + VOICE_HEADER_MAX);
        int commas = 0;
        int lengthStart = -1;
        int headerEnd = -1;
        for (int i = start + VOICE_HEAD.length; i < limit; i++) {
            if (in.getByte(i) == ',') {
                commas++;
                if (commas == VOICE_HEADER_COMMAS - 1) {
                    lengthStart = i + 1;
                } else if (commas == VOICE_HEADER_COMMAS) {
                    headerEnd = i + 1;
                    break;
                }
            }
        }
        if (headerEnd < 0) {
            if (in.readableBytes() >= VOICE_HEADER_MAX) {
                tooLong(ctx, in);
            }
            return;
        }
        int dataLength = parseDigits(in, lengthStart, headerEnd - 1);
        int total = headerEnd - start + Math.max(dataLength, 0) + 1;
        if (dataLength < 0 || total > maxFrameBytes) {
            tooLong(ctx, in);
            return;
        }
        if (in.readableBytes() < total) {
            return;
        }
        if (in.getByte(start + total - 1) != END) {
            // 长度字段和实际内容对不上，后面的字节无法再可靠地切包
            tooLong(ctx, in);
            return;
        }
        in.skipBytes(total);
        out.add(WatchPacket.VOICE);
    }

    private void tooLong(ChannelHandlerContext ctx, ByteBuf in) {
        metrics.dropped(DropReason.OVERSIZE);
        in.skipBytes(in.readableBytes());
        ctx.close();
    }

    private static boolean startsWith(ByteBuf in, byte[] prefix) {
        if (in.readableBytes() < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (in.getByte(in.readerIndex() + i) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** [from, to) 之间的十进制数字；不是纯数字返回 -1。 */
    private static int parseDigits(ByteBuf in, int from, int to) {
        if (from < 0 || to <= from || to - from > 6) {
            return -1;
        }
        int v = 0;
        for (int i = from; i < to; i++) {
            byte b = in.getByte(i);
            if (b < '0' || b > '9') {
                return -1;
            }
            v = v * 10 + (b - '0');
        }
        return v;
    }
}
