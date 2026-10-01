package com.xzkj.hv2.watch.buffer;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把一批缓冲数据写进库，一个事务（docs/03 第五节）：
 * <pre>
 * ├─ 插入 HEALTH_RECORD（MSG_ID 已存在的跳过）
 * ├─ 更新 HEALTH_LATEST（只接受更新的时间）
 * ├─ 更新 STEP_DAILY（覆盖为最新累计，只接受更晚的读数）
 * ├─ 更新 DEVICE 的电量（只接受更晚的时间）
 * └─ METRIC_COUNTER 加上本批实际新插入的条数
 * </pre>
 * 每一步重复做一遍结果不变，所以同一批数据被处理两次，既不会重复入库，也不会重复计数。
 * <p>
 * 用 JDBC 批量执行而不用 MyBatis：一批上千条，要逐条拿到"是否真的插入了"的行数来加计数。
 */
@Component
public class WatchBatchWriter {

    private static final String INSERT_RECORD = """
            MERGE INTO HEALTH_RECORD t
            USING (SELECT ? MSG_ID, ? COLLECTED_AT FROM DUAL) s
               ON (t.MSG_ID = s.MSG_ID AND t.COLLECTED_AT = s.COLLECTED_AT)
             WHEN NOT MATCHED THEN
                  INSERT (MSG_ID, CARD_CODE, METRIC, VAL1, VAL2, COLLECTED_AT, DEVICE_IMEI)
                  VALUES (s.MSG_ID, ?, ?, ?, ?, s.COLLECTED_AT, ?)""";

    /** 采集时间更晚的覆盖；同一秒时编号大的算新 */
    private static final String MERGE_LATEST = """
            MERGE INTO HEALTH_LATEST t
            USING (SELECT ? CARD_CODE, ? METRIC, ? VAL1, ? VAL2, ? COLLECTED_AT, ? MSG_ID, ? DEVICE_IMEI FROM DUAL) s
               ON (t.CARD_CODE = s.CARD_CODE AND t.METRIC = s.METRIC)
             WHEN MATCHED THEN UPDATE SET
                  t.VAL1 = s.VAL1, t.VAL2 = s.VAL2, t.COLLECTED_AT = s.COLLECTED_AT, t.MSG_ID = s.MSG_ID,
                  t.DEVICE_IMEI = s.DEVICE_IMEI
                  WHERE t.COLLECTED_AT < s.COLLECTED_AT OR (t.COLLECTED_AT = s.COLLECTED_AT AND t.MSG_ID < s.MSG_ID)
             WHEN NOT MATCHED THEN
                  INSERT (CARD_CODE, METRIC, VAL1, VAL2, COLLECTED_AT, MSG_ID, DEVICE_IMEI)
                  VALUES (s.CARD_CODE, s.METRIC, s.VAL1, s.VAL2, s.COLLECTED_AT, s.MSG_ID, s.DEVICE_IMEI)""";

    /** 覆盖，不累加；只接受更晚的读数 */
    private static final String MERGE_STEPS = """
            MERGE INTO STEP_DAILY t
            USING (SELECT ? STAT_DATE, ? CARD_CODE, ? STEPS, ? LAST_RAW, ? UPDATED_AT, ? DEVICE_IMEI FROM DUAL) s
               ON (t.STAT_DATE = s.STAT_DATE AND t.CARD_CODE = s.CARD_CODE)
             WHEN MATCHED THEN UPDATE SET
                  t.STEPS = s.STEPS, t.LAST_RAW = s.LAST_RAW, t.UPDATED_AT = s.UPDATED_AT, t.DEVICE_IMEI = s.DEVICE_IMEI
                  WHERE t.UPDATED_AT < s.UPDATED_AT
             WHEN NOT MATCHED THEN
                  INSERT (STAT_DATE, CARD_CODE, STEPS, LAST_RAW, UPDATED_AT, DEVICE_IMEI)
                  VALUES (s.STAT_DATE, s.CARD_CODE, s.STEPS, s.LAST_RAW, s.UPDATED_AT, s.DEVICE_IMEI)""";

    private static final String UPDATE_BATTERY = """
            UPDATE DEVICE SET BATTERY_PCT = ?, BATTERY_TIME = ?
             WHERE IMEI = ? AND (BATTERY_TIME IS NULL OR BATTERY_TIME < ?)""";

    private static final String ADD_COUNTER = "UPDATE METRIC_COUNTER SET TOTAL_COUNT = TOTAL_COUNT + ? WHERE METRIC = ?";

    private static final String UPDATE_LAST_SEEN = """
            UPDATE DEVICE SET LAST_SEEN_AT = ?
             WHERE IMEI = ? AND (LAST_SEEN_AT IS NULL OR LAST_SEEN_AT < ?)""";

    private final JdbcTemplate jdbc;

    public WatchBatchWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 写一批。任何一步失败整个事务回滚，由调用方决定重试还是拆开。
     *
     * @return 本批真正新插入 HEALTH_RECORD 的条数，按指标
     */
    @Transactional
    public Map<String, Integer> write(List<WatchDatum> batch) {
        List<WatchDatum> vitals = batch.stream().filter(WatchDatum::isVital).toList();
        List<WatchDatum> beats = batch.stream().filter(d -> !d.isVital()).toList();

        Map<String, Integer> inserted = insertRecords(vitals);
        mergeLatest(vitals);
        mergeSteps(beats);
        updateBattery(beats);
        inserted.forEach((metric, n) -> {
            if (n > 0) {
                jdbc.update(ADD_COUNTER, n, metric);
            }
        });
        return inserted;
    }

    /** 最后上行时间（来自内存，每轮写一次）；只接受更晚的时间。 */
    public void updateLastSeen(Map<String, LocalDateTime> lastSeen) {
        List<Object[]> args = new ArrayList<>(lastSeen.size());
        lastSeen.forEach((imei, at) -> args.add(new Object[] {Timestamp.valueOf(at), imei, Timestamp.valueOf(at)}));
        if (!args.isEmpty()) {
            jdbc.batchUpdate(UPDATE_LAST_SEEN, args);
        }
    }

    private Map<String, Integer> insertRecords(List<WatchDatum> vitals) {
        Map<String, Integer> inserted = new TreeMap<>();
        if (vitals.isEmpty()) {
            return inserted;
        }
        List<Object[]> args = new ArrayList<>(vitals.size());
        for (WatchDatum d : vitals) {
            args.add(new Object[] {d.msgId(), Timestamp.valueOf(d.collectedAt()), d.card(), d.metric(), d.val1(),
                    d.val2(), d.imei()});
        }
        int[] counts = jdbc.batchUpdate(INSERT_RECORD, args);
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] < 0) {
                // Oracle 驱动拿不到逐条行数时会返回负数；那样没法保证计数不多算，宁可报错回滚
                throw new IllegalStateException("批量写入没有返回逐条行数：" + counts[i]);
            }
            inserted.merge(vitals.get(i).metric(), counts[i], Integer::sum);
        }
        return inserted;
    }

    private void mergeLatest(List<WatchDatum> vitals) {
        Comparator<WatchDatum> order = Comparator.comparing(WatchDatum::collectedAt).thenComparingLong(WatchDatum::msgId);
        List<Object[]> args = latestBy(vitals, d -> d.card() + "|" + d.metric(), order).stream()
                .map(d -> new Object[] {d.card(), d.metric(), d.val1(), d.val2(), Timestamp.valueOf(d.collectedAt()),
                        d.msgId(), d.imei()})
                .toList();
        if (!args.isEmpty()) {
            jdbc.batchUpdate(MERGE_LATEST, args);
        }
    }

    private void mergeSteps(List<WatchDatum> beats) {
        List<WatchDatum> withSteps = beats.stream().filter(d -> d.card() != null && d.steps() != null).toList();
        List<Object[]> args = latestBy(withSteps, d -> d.statDate() + "|" + d.card(), Comparator.comparingLong(WatchDatum::at))
                .stream()
                .map(d -> new Object[] {Date.valueOf(d.statDate()), d.card(), d.steps(), d.raw(),
                        Timestamp.valueOf(d.receivedAt()), d.imei()})
                .toList();
        if (!args.isEmpty()) {
            jdbc.batchUpdate(MERGE_STEPS, args);
        }
    }

    private void updateBattery(List<WatchDatum> beats) {
        List<WatchDatum> withBattery = beats.stream().filter(d -> d.battery() != null).toList();
        List<Object[]> args = latestBy(withBattery, WatchDatum::imei, Comparator.comparingLong(WatchDatum::at)).stream()
                .map(d -> new Object[] {d.battery(), Timestamp.valueOf(d.collectedAt()), d.imei(),
                        Timestamp.valueOf(d.collectedAt())})
                .toList();
        if (!args.isEmpty()) {
            jdbc.batchUpdate(UPDATE_BATTERY, args);
        }
    }

    /** 同一个键只留最新的一条（一批里同一个人同一指标可能有多条，只需要写最新的）。 */
    private static List<WatchDatum> latestBy(List<WatchDatum> items, Function<WatchDatum, String> key,
                                             Comparator<WatchDatum> order) {
        Map<String, WatchDatum> latest = new LinkedHashMap<>();
        for (WatchDatum d : items) {
            latest.merge(key.apply(d), d, (a, b) -> order.compare(a, b) >= 0 ? a : b);
        }
        return new ArrayList<>(latest.values());
    }
}
