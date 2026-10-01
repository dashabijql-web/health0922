package com.xzkj.hv2.watch;

import java.util.Set;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 手表链路的计数类指标（docs/03 第八节）。连接数、在线数、缓冲积压这类"当前值"由各自的类注册成 gauge。
 */
@Component
public class WatchMetrics {

    /** 协议号作为标签；不认识的协议号统一记成 OTHER，防止乱发的包把标签撑爆。 */
    private static final Set<String> KNOWN_CODES = Set.of("AP00", "AP01", "AP02", "AP03", "AP07", "AP10", "AP49",
            "AP50", "APHT", "APHP", "APXL", "APXY", "APXZ", "APXT", "AP33", "AP86", "AP87");

    private final MeterRegistry meters;

    public WatchMetrics(MeterRegistry meters) {
        this.meters = meters;
    }

    public MeterRegistry registry() {
        return meters;
    }

    public void packet(String code) {
        meters.counter("health.watch.packets", "code", KNOWN_CODES.contains(code) ? code : "OTHER").increment();
    }

    public void dropped(DropReason reason) {
        dropped(reason, 1);
    }

    public void dropped(DropReason reason, int n) {
        meters.counter("health.watch.dropped", "reason", reason.tag()).increment(n);
    }

    /** 丢弃原因（指标 health.watch.dropped 的标签）。 */
    public enum DropReason {
        /** 体征为 0 或空（没戴好） */
        ZERO("zero"),
        /** APHP 的未佩戴占位包 0,0,0,95,0.0,0.0 */
        PLACEHOLDER("placeholder"),
        /** 超出合理范围 */
        OUT_OF_RANGE("out_of_range"),
        /** 数字格式不对、字段缺失 */
        MALFORMED("malformed"),
        /** 手表已登记、未绑定：体征不入库 */
        UNBOUND("unbound"),
        /** 手表未登记（或已停用）：数据不入库 */
        UNREGISTERED("unregistered"),
        /** 没登录就发数据的连接 */
        NOT_LOGGED_IN("not_logged_in"),
        /** 超过 8 KB 或切不出完整包 */
        OVERSIZE("oversize"),
        /** AP10 里不认识的报警代码 */
        UNKNOWN_ALARM("unknown_alarm"),
        /** 连接数已满，新连接被关闭 */
        CONNECTION_LIMIT("connection_limit");

        private final String tag;

        DropReason(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }
}
