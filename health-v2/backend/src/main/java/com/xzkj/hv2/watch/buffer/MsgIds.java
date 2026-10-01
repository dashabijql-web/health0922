package com.xzkj.hv2.watch.buffer;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 数据的唯一编号 MSG_ID：毫秒时间戳 × 1000 + 序号（docs/03 第五节）。
 * 同一毫秒超过 1000 条时借用下一毫秒的号段，编号仍然唯一、递增；重启后时间戳变大，不会和重启前重复。
 */
@Component
public class MsgIds {

    private final AtomicLong last = new AtomicLong();

    public long next(long nowMillis) {
        long base = nowMillis * 1000;
        return last.updateAndGet(prev -> Math.max(prev + 1, base));
    }
}
