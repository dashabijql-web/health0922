package com.xzkj.hv2ops;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 稳定性观察（docs/08 阶段 7 验收 A11、docs/11"稳定性观察与压测"）：每隔一段时间从后端的 Actuator 取一次指标，
 * 写进 CSV，结束时打印摘要。只读 /actuator/metrics，不需要登录，所以要在后端所在的机器上运行（上线时它只对本机开放）。
 * <pre>
 *   java -jar hv2-ops.jar soak --url http://127.0.0.1:8081 --minutes 1440 --out soak.csv
 *   java -jar hv2-ops.jar soak-summary soak.csv          对已有的 CSV 重新出摘要
 * </pre>
 */
final class Soak {

    static final List<String> COLUMNS = List.of("time", "up", "connections", "online", "buffer", "flushes",
            "flushAvgMs", "flushMaxMs", "packets", "dropped", "dead", "memoryDropped", "summaryRuns", "summaryMaxMs",
            "posDone", "posFailed", "heapMb", "threads", "cpu", "dbActive");

    private static final Pattern MEASURE = Pattern.compile(
            "\"statistic\"\\s*:\\s*\"(\\w+)\"\\s*,\\s*\"value\"\\s*:\\s*([-0-9.Ee+]+|\"NaN\")");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base;
    /** 上一次的累计值，用来算这一分钟的增量 */
    private final Map<String, Double> last = new LinkedHashMap<>();

    private Soak(String base) {
        this.base = base.replaceAll("/+$", "");
    }

    static int run(List<String> args) {
        String url = option(args, "--url", "http://127.0.0.1:8081");
        long minutes = Long.parseLong(option(args, "--minutes", "1440"));
        long interval = Long.parseLong(option(args, "--interval", "60"));
        Path out = Path.of(option(args, "--out", "soak.csv"));
        Soak soak = new Soak(url);
        long end = System.currentTimeMillis() + minutes * 60_000;
        System.out.println("每 " + interval + " 秒采样一次，共 " + minutes + " 分钟，写到 " + out.toAbsolutePath());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println(String.join(",", COLUMNS));
            int n = 0;
            while (true) {
                long started = System.currentTimeMillis();
                Map<String, String> row = soak.sample();
                w.println(String.join(",", COLUMNS.stream().map(c -> row.getOrDefault(c, "")).toList()));
                w.flush();
                if (++n % 10 == 1) {
                    System.out.println(row.get("time") + " 后端" + ("1".equals(row.get("up")) ? "正常" : "无响应")
                            + " 连接 " + row.get("connections") + " 在线 " + row.get("online")
                            + " 缓冲 " + row.get("buffer") + " 堆 " + row.get("heapMb") + " MB");
                }
                long next = started + interval * 1000;
                if (next > end) {
                    break;
                }
                Thread.sleep(Math.max(0, next - System.currentTimeMillis()));
            }
        } catch (IOException e) {
            throw new OpsException("写 " + out + " 失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(summary(out));
        return 0;
    }

    static int summaryOnly(List<String> args) {
        if (args.isEmpty()) {
            throw new OpsException("用法：soak-summary <soak.csv>");
        }
        System.out.println(summary(Path.of(args.get(0))));
        return 0;
    }

    private Map<String, String> sample() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("time", LocalDateTime.now().format(TIME));
        Map<String, Double> health = metric("health.watch.connections");
        if (health == null) {
            row.put("up", "0");
            return row;
        }
        row.put("up", "1");
        row.put("connections", fmt(health.get("VALUE")));
        row.put("online", fmt(value("health.watch.online.count", "VALUE")));
        row.put("buffer", fmt(value("health.buffer.size", "VALUE")));
        Map<String, Double> flush = metric("health.buffer.flush.duration");
        double flushes = delta("flushCount", get(flush, "COUNT"));
        double flushSeconds = delta("flushTotal", get(flush, "TOTAL_TIME"));
        row.put("flushes", fmt(flushes));
        row.put("flushAvgMs", flushes > 0 ? fmt1(flushSeconds / flushes * 1000) : "");
        row.put("flushMaxMs", fmt1(get(flush, "MAX") * 1000));
        row.put("packets", fmt(delta("packets", value("health.watch.packets", "COUNT"))));
        row.put("dropped", fmt(delta("dropped", value("health.watch.dropped", "COUNT"))));
        row.put("dead", fmt(delta("dead", value("health.buffer.dead", "COUNT"))));
        row.put("memoryDropped", fmt(delta("memoryDropped", value("health.buffer.memory.dropped", "COUNT"))));
        Map<String, Double> summary = metric("health.summary.duration");
        row.put("summaryRuns", fmt(delta("summaryCount", get(summary, "COUNT"))));
        row.put("summaryMaxMs", fmt1(get(summary, "MAX") * 1000));
        row.put("posDone", fmt(delta("posDone", value("hv2.positioning.files?tag=status:DONE", "COUNT"))));
        row.put("posFailed", fmt(delta("posFailed", value("hv2.positioning.files?tag=status:FAILED", "COUNT"))));
        row.put("heapMb", fmt1(value("jvm.memory.used?tag=area:heap", "VALUE") / 1024 / 1024));
        row.put("threads", fmt(value("jvm.threads.live", "VALUE")));
        row.put("cpu", String.format(Locale.ROOT, "%.3f", value("process.cpu.usage", "VALUE")));
        row.put("dbActive", fmt(value("hikaricp.connections.active", "VALUE")));
        return row;
    }

    /** 累计计数的增量；第一次采样或计数被重置（后端重启）时记 0 */
    private double delta(String key, double now) {
        Double prev = last.put(key, now);
        return prev == null || now < prev ? 0 : now - prev;
    }

    private double value(String name, String statistic) {
        return get(metric(name), statistic);
    }

    private static double get(Map<String, Double> m, String statistic) {
        if (m == null) {
            return 0;
        }
        Double v = m.get(statistic);
        return v == null || v.isNaN() ? 0 : v;
    }

    /** 取一个指标；指标还没出现（比如还没处理过定位文件）或后端无响应时返回 null */
    private Map<String, Double> metric(String nameAndQuery) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/actuator/metrics/" + nameAndQuery))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                return null;
            }
            Map<String, Double> m = new LinkedHashMap<>();
            Matcher matcher = MEASURE.matcher(r.body());
            while (matcher.find()) {
                String v = matcher.group(2);
                m.put(matcher.group(1), v.startsWith("\"") ? Double.NaN : Double.parseDouble(v));
            }
            return m;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ---- 摘要 ----

    static String summary(Path csv) {
        List<Map<String, String>> rows = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
            List<String> head = List.of(lines.get(0).split(",", -1));
            for (String line : lines.subList(1, lines.size())) {
                String[] f = line.split(",", -1);
                Map<String, String> r = new LinkedHashMap<>();
                for (int i = 0; i < head.size() && i < f.length; i++) {
                    r.put(head.get(i), f[i]);
                }
                rows.add(r);
            }
        } catch (IOException | IndexOutOfBoundsException e) {
            throw new OpsException("读不了 " + csv);
        }
        List<Map<String, String>> up = rows.stream().filter(r -> "1".equals(r.get("up"))).toList();
        if (up.isEmpty()) {
            return "没有一次采样成功（后端一直无响应？）";
        }
        int hour = (int) Math.min(60, Math.max(1, up.size() / 4));
        List<Map<String, String>> first = up.subList(0, hour);
        List<Map<String, String>> lastHour = up.subList(up.size() - hour, up.size());
        StringBuilder b = new StringBuilder();
        b.append("==== 稳定性观察摘要 ====\n");
        b.append("时间：").append(rows.get(0).get("time")).append(" 到 ").append(rows.get(rows.size() - 1).get("time"))
                .append("，采样 ").append(rows.size()).append(" 次，后端无响应 ").append(rows.size() - up.size()).append(" 次\n");
        b.append("连接数：").append(range(up, "connections")).append("；在线：").append(range(up, "online")).append('\n');
        b.append("缓冲积压：最大 ").append(fmt(max(up, "buffer"))).append("，开头 ").append(hour).append(" 次采样最大 ")
                .append(fmt(max(first, "buffer"))).append("，最后 ").append(hour).append(" 次最大 ")
                .append(fmt(max(lastHour, "buffer"))).append('\n');
        b.append("每批写库平均：开头 ").append(fmt1(weightedFlush(first))).append(" ms，最后 ")
                .append(fmt1(weightedFlush(lastHour))).append(" ms，全程 ").append(fmt1(weightedFlush(up)))
                .append(" ms；最慢一批约 ").append(fmt1(max(up, "flushMaxMs"))).append(" ms\n");
        b.append("堆内存平均：开头 ").append(fmt1(avg(first, "heapMb"))).append(" MB，最后 ")
                .append(fmt1(avg(lastHour, "heapMb"))).append(" MB；线程：开头 ").append(fmt(avg(first, "threads")))
                .append("，最后 ").append(fmt(avg(lastHour, "threads"))).append('\n');
        b.append("日汇总：重算 ").append(fmt(sum(up, "summaryRuns"))).append(" 次，最慢约 ")
                .append(fmt1(max(up, "summaryMaxMs"))).append(" ms\n");
        b.append("收包 ").append(fmt(sum(up, "packets"))).append("，丢弃 ").append(fmt(sum(up, "dropped")))
                .append("，进死信 ").append(fmt(sum(up, "dead"))).append("，降级队列丢弃 ")
                .append(fmt(sum(up, "memoryDropped"))).append('\n');
        b.append("定位文件：入库 ").append(fmt(sum(up, "posDone"))).append(" 份，失败 ")
                .append(fmt(sum(up, "posFailed"))).append(" 份\n");
        b.append("（\"开头\"\"最后\"各取 ").append(hour).append(" 次采样；判断：缓冲不持续增长、写库耗时和堆内存不随时间上涨、"
                + "没有死信和降级丢弃，见 docs/11）");
        return b.toString();
    }

    private static double weightedFlush(List<Map<String, String>> rows) {
        double count = 0;
        double total = 0;
        for (Map<String, String> r : rows) {
            double n = num(r, "flushes");
            count += n;
            total += n * num(r, "flushAvgMs");
        }
        return count == 0 ? 0 : total / count;
    }

    private static String range(List<Map<String, String>> rows, String col) {
        return fmt(min(rows, col)) + "–" + fmt(max(rows, col));
    }

    private static double min(List<Map<String, String>> rows, String col) {
        return rows.stream().mapToDouble(field(col)).min().orElse(0);
    }

    private static double max(List<Map<String, String>> rows, String col) {
        return rows.stream().mapToDouble(field(col)).max().orElse(0);
    }

    private static double avg(List<Map<String, String>> rows, String col) {
        return rows.stream().mapToDouble(field(col)).average().orElse(0);
    }

    private static double sum(List<Map<String, String>> rows, String col) {
        return rows.stream().mapToDouble(field(col)).sum();
    }

    private static ToDoubleFunction<Map<String, String>> field(String col) {
        return r -> num(r, col);
    }

    private static double num(Map<String, String> r, String col) {
        String v = r.get(col);
        return v == null || v.isEmpty() ? 0 : Double.parseDouble(v);
    }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v));
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String option(List<String> args, String name, String def) {
        int i = args.indexOf(name);
        return i >= 0 && i + 1 < args.size() ? args.get(i + 1) : def;
    }
}
