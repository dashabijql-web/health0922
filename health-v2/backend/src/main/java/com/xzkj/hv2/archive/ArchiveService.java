package com.xzkj.hv2.archive;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xzkj.hv2.alert.AlertLabels;
import com.xzkj.hv2.archive.ArchiveViews.AgeResult;
import com.xzkj.hv2.archive.ArchiveViews.AlertRecord;
import com.xzkj.hv2.archive.ArchiveViews.ArchivePerson;
import com.xzkj.hv2.archive.ArchiveViews.Battery;
import com.xzkj.hv2.archive.ArchiveViews.Filters;
import com.xzkj.hv2.archive.ArchiveViews.ListEntry;
import com.xzkj.hv2.archive.ArchiveViews.PersonDetail;
import com.xzkj.hv2.archive.ArchiveViews.Position;
import com.xzkj.hv2.archive.ArchiveViews.StepDay;
import com.xzkj.hv2.archive.ArchiveViews.StepDays;
import com.xzkj.hv2.archive.ArchiveViews.TodaySteps;
import com.xzkj.hv2.archive.ArchiveViews.Trend;
import com.xzkj.hv2.archive.ArchiveViews.TrendPoint;
import com.xzkj.hv2.archive.ArchiveViews.Vital;
import com.xzkj.hv2.archive.ArchiveViews.Watch;
import com.xzkj.hv2.auth.SessionGateway;
import com.xzkj.hv2.common.api.PageResult;
import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.common.oplog.OperationAction;
import com.xzkj.hv2.common.oplog.OperationLogService;
import com.xzkj.hv2.positioning.StationNames;
import com.xzkj.hv2.watch.WatchProperties;

/**
 * 健康档案、个人档案（docs/05 第六、七节）。"现在""今天"一律取注入的 {@link Clock}。
 * 年龄是唯一允许人工修改的人员字段，改动和操作日志在同一个事务里。
 */
@Service
public class ArchiveService {

    /** 四项体征，也是 trend 接口 metric 的取值 */
    public static final List<String> VITALS = List.of("HEART_RATE", "SPO2", "TEMPERATURE", "BLOOD_PRESSURE");
    static final int MIN_AGE = 16;
    static final int MAX_AGE = 75;
    static final String TARGET = "PERSON";

    private final ArchiveMapper mapper;
    private final OperationLogService oplog;
    private final SessionGateway session;
    private final Clock clock;
    private final int onlineWindowMinutes;
    private final int vitalStaleMinutes;

    public ArchiveService(ArchiveMapper mapper, OperationLogService oplog, SessionGateway session, Clock clock,
                          WatchProperties watchProps) {
        this.mapper = mapper;
        this.oplog = oplog;
        this.session = session;
        this.clock = clock;
        this.onlineWindowMinutes = watchProps.onlineWindowMinutes();
        this.vitalStaleMinutes = watchProps.vitalStaleMinutes();
    }

    /** 操作日志里年龄改动前后的样子：{ "age": 45 } */
    record AgeLog(Integer age) {
    }

    // ================= 健康档案列表 =================

    public Filters filters() {
        return new Filters(mapper.depts(), mapper.jobKinds());
    }

    public PageResult<ArchivePerson> persons(String dept, String jobKind, String keyword, int page, int size) {
        String d = blankToNull(dept);
        String j = blankToNull(jobKind);
        String k = blankToNull(keyword);
        int total = mapper.countPersons(d, j, k);
        List<ArchivePerson> list = total == 0 ? List.of()
                : mapper.persons(d, j, k, (page - 1) * size, size).stream()
                .map(r -> new ArchivePerson(r.cardCode(), r.personName(), r.jobKind(), r.dept(), r.age()))
                .toList();
        return new PageResult<>(total, page, size, list);
    }

    // ================= 个人档案 =================

    public PersonDetail detail(String cardCode) {
        ArchiveMapper.DetailRow r = mapper.detail(cardCode);
        if (r == null) {
            throw notFound();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        Watch watch;
        Battery battery = null;
        if (r.imei() == null) {
            watch = new Watch("UNBOUND", null);
        } else {
            boolean online = r.lastSeenAt() != null && !r.lastSeenAt().isBefore(now.minusMinutes(onlineWindowMinutes));
            watch = new Watch(online ? "ONLINE" : "OFFLINE", r.lastSeenAt());
            if (r.batteryPct() != null) {
                battery = new Battery(r.batteryPct(), r.batteryTime());
            }
        }

        Map<String, Vital> vitals = new LinkedHashMap<>();
        VITALS.forEach(m -> vitals.put(m, null));
        LocalDateTime staleBefore = now.minusMinutes(vitalStaleMinutes);
        for (ArchiveMapper.VitalRow v : mapper.latestVitals(cardCode)) {
            vitals.put(v.metric(), new Vital(v.val1(), v.val2(), v.collectedAt(),
                    v.collectedAt().isBefore(staleBefore)));
        }

        ArchiveMapper.StepRow s = mapper.todaySteps(cardCode, today);
        TodaySteps steps = s == null ? null : new TodaySteps(s.steps(), s.updatedAt());

        Position position = r.inWell() == 0 ? null
                : new Position(r.areaName(),
                StationNames.resolve(r.displayName(), r.vendorName(), r.stationAreaName(), r.stationCode()),
                r.x() != null, r.posTime(),
                r.runStatus() != null && (r.runStatus() == 1 || r.runStatus() == 2));

        Map<String, ListEntry> lists = new LinkedHashMap<>();
        lists.put("KEY", null);
        lists.put("TODAY", null);
        for (ArchiveMapper.ListRow l : mapper.listsOf(cardCode, today)) {
            lists.put(l.listType(), new ListEntry(l.id(), l.note(), l.expireDate(), l.addedBy(), l.addedAt()));
        }

        return new PersonDetail(r.cardCode(), r.personName(), r.jobKind(), r.dept(), r.age(), r.ageUpdatedBy(),
                r.ageUpdatedAt(), watch, battery, vitals, steps, position, lists);
    }

    /**
     * 某天某项体征的原始读数。
     *
     * @param date null 表示今天
     */
    public Trend trend(String cardCode, String metric, LocalDate date) {
        requirePerson(cardCode);
        LocalDate day = date != null ? date : LocalDate.now(clock);
        List<TrendPoint> points = mapper.trend(cardCode, metric, day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                .stream().map(p -> new TrendPoint(p.collectedAt(), p.val1(), p.val2())).toList();
        return new Trend(metric, day, vitalStaleMinutes, points);
    }

    /** 到今天为止最近 days 天的步数，从早到晚，那天没有记录时为 null。 */
    public StepDays steps(String cardCode, int days) {
        requirePerson(cardCode);
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays(days - 1L);
        Map<LocalDate, Long> byDay = new HashMap<>();
        mapper.steps(cardCode, from, to).forEach(r -> byDay.put(r.statDate(), r.steps()));
        List<StepDay> list = new ArrayList<>(days);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            list.add(new StepDay(d, byDay.get(d)));
        }
        return new StepDays(list);
    }

    /** 预警记录：这个人的全部事件（六类告警和设备事件），最新的在前。 */
    public PageResult<AlertRecord> alerts(String cardCode, int page, int size) {
        requirePerson(cardCode);
        int total = mapper.countAlerts(cardCode);
        List<AlertRecord> list = total == 0 ? List.of()
                : mapper.alerts(cardCode, (page - 1) * size, size).stream()
                .map(a -> new AlertRecord(a.id(), a.occurredAt(), a.lastOccurredAt(), a.src(), a.category(),
                        AlertLabels.codeName(a.code()),
                        // 设备报警的 VAL_TEXT 是手表的报警代码，不是体征值，不显示
                        "THRESHOLD".equals(a.src()) ? a.valText() : null, a.occurCount()))
                .toList();
        return new PageResult<>(total, page, size, list);
    }

    // ================= 年龄 =================

    /** 录入年龄（16–75）。和原来一样时不改、不记日志。 */
    @Transactional
    public AgeResult setAge(String cardCode, int age) {
        if (age < MIN_AGE || age > MAX_AGE) {
            throw BizException.badRequest("年龄应在 " + MIN_AGE + "–" + MAX_AGE + " 之间");
        }
        ArchiveMapper.AgeRow current = mapper.lockAge(cardCode);
        if (current == null) {
            throw notFound();
        }
        if (!Objects.equals(current.age(), age)) {
            String username = session.currentUser().username();
            mapper.updateAge(cardCode, age, username, LocalDateTime.now(clock));
            oplog.record(OperationAction.PERSON_AGE_SET, TARGET, cardCode, new AgeLog(current.age()), new AgeLog(age));
        }
        ArchiveMapper.DetailRow r = mapper.detail(cardCode);
        return new AgeResult(age, r.ageUpdatedBy(), r.ageUpdatedAt());
    }

    // ================= 工具 =================

    void requirePerson(String cardCode) {
        if (!mapper.personExists(cardCode)) {
            throw notFound();
        }
    }

    static BizException notFound() {
        return new BizException(HttpStatus.NOT_FOUND, "没有这个人");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
