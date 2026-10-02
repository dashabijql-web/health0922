package com.xzkj.hv2.report;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.report.MonthlyReportViews.Months;
import com.xzkj.hv2.report.MonthlyReportViews.MonthlyReport;

/**
 * 月度汇总（docs/07 第二部分）：月份列表、一个月的报告。"现在""今天"一律取注入的 {@link Clock}。
 * <p>
 * 缓存：已结束的月份算一次缓存 1 天（{@code hv2.report.cache-ttl}），当前月每次重算。上个月最后一天的日汇总
 * 在本月 1 号 0:10 才定稿，所以从本月 2 号起上个月才算"已结束"。缓存最多留 24 个月，放在本进程内存里，
 * 重启后清空。耗时记到指标 health.report.duration。
 */
@Service
public class MonthlyReportService {

    static final int CACHE_MONTHS = 24;

    private final MonthlyReportCalculator calculator;
    private final MonthlyReportMapper mapper;
    private final Clock clock;
    private final Duration ttl;
    private final Timer timer;
    private final Map<YearMonth, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<YearMonth, Cached> eldest) {
            return size() > CACHE_MONTHS;
        }
    };

    public MonthlyReportService(MonthlyReportCalculator calculator, MonthlyReportMapper mapper, Clock clock,
                                ReportProperties props, MeterRegistry meters) {
        this.calculator = calculator;
        this.mapper = mapper;
        this.clock = clock;
        this.ttl = props.cacheTtl();
        this.timer = Timer.builder("health.report.duration").description("算一份月报的耗时").register(meters);
    }

    private record Cached(MonthlyReport report, LocalDateTime expiresAt) {
    }

    /** 有数据的月份；默认上个月，上个月没有数据时取最近有数据的月份。 */
    public Months months() {
        LocalDate today = LocalDate.now(clock);
        List<String> months = mapper.months(today);
        String previous = YearMonth.from(today).minusMonths(1).toString();
        String def = months.contains(previous) ? previous : months.isEmpty() ? null : months.getLast();
        return new Months(months, def);
    }

    public MonthlyReport monthly(YearMonth month) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (month.isAfter(YearMonth.from(now))) {
            throw BizException.badRequest("不能查看还没到的月份");
        }
        boolean ended = !now.toLocalDate().isBefore(month.plusMonths(1).atDay(2));
        if (ended) {
            synchronized (cache) {
                Cached c = cache.get(month);
                if (c != null && now.isBefore(c.expiresAt())) {
                    return c.report();
                }
            }
        }
        long start = System.nanoTime();
        MonthlyReport report = calculator.compute(month, now);
        timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        if (ended) {
            synchronized (cache) {
                cache.put(month, new Cached(report, now.plus(ttl)));
            }
        }
        return report;
    }

    /** 清空缓存（测试用）。 */
    void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }
}
