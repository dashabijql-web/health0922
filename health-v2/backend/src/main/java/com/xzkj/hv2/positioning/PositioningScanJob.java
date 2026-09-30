package com.xzkj.hv2.positioning;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每隔 POSITIONING_SCAN_SECONDS 秒扫描一次收件箱；上一轮没做完不会开始下一轮。 */
@Component
@ConditionalOnProperty(name = "hv2.positioning.scan-enabled", havingValue = "true")
public class PositioningScanJob {

    private final PositioningInbox inbox;

    public PositioningScanJob(PositioningInbox inbox) {
        this.inbox = inbox;
    }

    @Scheduled(initialDelayString = "${hv2.positioning.scan-seconds}",
            fixedDelayString = "${hv2.positioning.scan-seconds}", timeUnit = TimeUnit.SECONDS)
    public void scan() {
        inbox.scanOnce();
    }
}
