package com.xzkj.hv2.health;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * HEALTH_DAILY_SUMMARY 的定时任务（docs/04 第五节）：每 5 分钟重算"今天"，每天 0:10 把"昨天"重算定稿。
 * 每次都整天重算（MERGE），重复执行结果不变。耗时记到指标 health.summary.duration。
 */
@Component
@ConditionalOnProperty(name = "hv2.health.summary-enabled", havingValue = "true")
public class HealthDailySummaryJob {

    private static final Logger log = LoggerFactory.getLogger(HealthDailySummaryJob.class);

    private final HealthSummaryMapper mapper;
    private final Clock clock;
    private final Timer timer;

    public HealthDailySummaryJob(HealthSummaryMapper mapper, Clock clock, MeterRegistry meters) {
        this.mapper = mapper;
        this.clock = clock;
        this.timer = Timer.builder("health.summary.duration").description("重算一天日汇总的耗时").register(meters);
    }

    @Scheduled(initialDelay = 1, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void refreshToday() {
        run(LocalDate.now(clock));
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Shanghai")
    public void finalizeYesterday() {
        run(LocalDate.now(clock).minusDays(1));
    }

    /** 重算某一天，返回写入的行数；数据库出错时记日志，返回 -1。 */
    public int run(LocalDate day) {
        long start = System.nanoTime();
        try {
            int rows = mapper.mergeDay(day, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            timer.record(ms, TimeUnit.MILLISECONDS);
            log.info("日汇总 {} 重算完成：{} 行，耗时 {} ms", day, rows, ms);
            return rows;
        } catch (DataAccessException e) {
            log.warn("日汇总 {} 重算失败：{}", day, e.getMessage());
            return -1;
        }
    }
}
