package com.xzkj.hv2.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 测试里可以拨动的时钟。 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    public MutableClock(LocalDateTime now, ZoneId zone) {
        this.zone = zone;
        this.instant = now.atZone(zone).toInstant();
    }

    public void set(LocalDateTime now) {
        this.instant = now.atZone(zone).toInstant();
    }

    public void advance(Duration d) {
        this.instant = instant.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
