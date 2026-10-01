package com.xzkj.hv2.positioning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xzkj.hv2.common.oplog.OperationAction;
import com.xzkj.hv2.common.oplog.OperationLogService;

/**
 * 把解析好的定位文件写进数据库（docs/02 第四节）。一个文件一个事务：要么全部写入，要么全部作废。
 * <p>
 * 新旧按文件头里的"数据上传时间"判断，不看到达先后：各表的 MERGE 只让更新的数据覆盖旧数据；
 * 历史类的表（每日出入井、井下人数曲线）旧文件照常写入。
 */
@Service
public class PositioningIngestService {

    /** 一个文件最多往 POS_INGEST_ERROR 写多少条，防止格式整体变了时写入几千条 */
    static final int MAX_ISSUES_PER_FILE = 500;

    private static final Logger log = LoggerFactory.getLogger(PositioningIngestService.class);

    private final PositioningMapper mapper;
    private final OperationLogService oplog;

    public PositioningIngestService(PositioningMapper mapper, OperationLogService oplog) {
        this.mapper = mapper;
        this.oplog = oplog;
    }

    /** 操作日志里年龄被清空前后的样子：{ "age": 45 } → { "age": null, "reason": "…" } */
    record AgeBefore(Integer age) {
    }

    record AgeCleared(Integer age, String reason) {
    }

    /**
     * 写入各表并记一行处理记录。
     *
     * @param file 处理记录里除状态、条数以外的信息（文件名、摘要、备份位置等）
     * @return 这个文件的最终状态：DONE、PARTIAL 或 STALE
     */
    @Transactional
    public IngestStatus ingest(ParsedFile parsed, FileFacts file) {
        LocalDateTime src = parsed.headerTime();
        LocalDateTime latest = mapper.latestEffectiveHeaderTime(parsed.type().name());
        boolean stale = latest != null && src.isBefore(latest);
        List<RecordIssue> issues = new ArrayList<>(parsed.issues());

        switch (parsed.type()) {
            case RYQY -> parsed.rowsOf(PositioningRow.Area.class).forEach(r -> mapper.mergeArea(r, src));
            case RYJZ -> parsed.rowsOf(PositioningRow.StationName.class).forEach(r -> mapper.mergeStationName(r, src));
            case JZSS -> parsed.rowsOf(PositioningRow.StationStatus.class)
                    .forEach(r -> mapper.mergeStationStatus(r, src));
            case RYXX -> issues.addAll(writePeople(parsed, src));
            case RYSS -> writeSnapshot(parsed.rowsOf(PositioningRow.PersonState.class), src);
        }

        int errors = parsed.errorCount();
        IngestStatus status = stale ? IngestStatus.STALE : errors > 0 ? IngestStatus.PARTIAL : IngestStatus.DONE;
        String msg = stale ? "文件头时间早于已生效的 " + latest + "，不作为最新数据"
                : errors > 0 ? errors + " 条记录有误，已跳过" : null;
        record(file.toEntry(parsed.type().name(), src, status, parsed.recordCount() - errors, errors, msg), issues);
        return status;
    }

    /**
     * RYXX：按卡编码更新人员（docs/02 第四节）。卡编码就代表人（docs/00 第 21 项）：要生效的记录里姓名和库里不同，
     * 说明卡可能给了别人——清空人工录入的年龄（写操作日志），记一条警告提示人工核对。手表绑定、名单不动，由人核对后处理。
     *
     * @return 姓名变了的警告，和解析时发现的问题一起写进 POS_INGEST_ERROR
     */
    private List<RecordIssue> writePeople(ParsedFile parsed, LocalDateTime src) {
        Map<String, PositioningMapper.PersonNow> current = new HashMap<>();
        mapper.peopleNow().forEach(p -> current.put(p.cardCode(), p));
        List<RecordIssue> warnings = new ArrayList<>();
        for (PositioningRow.Person r : parsed.rowsOf(PositioningRow.Person.class)) {
            PositioningMapper.PersonNow before = current.get(r.cardCode());
            if (before != null && nameChanged(before, r, src)) {
                if (before.age() != null) {
                    mapper.clearAge(r.cardCode());
                    oplog.recordSystem(OperationAction.PERSON_AGE_SET, "PERSON", r.cardCode(),
                            new AgeBefore(before.age()), new AgeCleared(null, "定位文件里这个卡编码换了姓名"));
                }
                // 姓名只进库（POS_INGEST_ERROR 页面不展示），不写进日志
                warnings.add(RecordIssue.warn(parsed.lineOf(r), r.cardCode() + ";" + r.personName(),
                        "卡编码换了姓名（原：" + before.personName() + "）"
                                + (before.age() != null ? "，已清空人工录入的年龄 " + before.age() : "")
                                + "，请人工核对手表绑定和名单"));
                log.warn("RYXX 里卡编码 {} 换了姓名{}，请人工核对", r.cardCode(),
                        before.age() != null ? "，已清空人工录入的年龄" : "");
            }
            mapper.mergePerson(r, src);
        }
        return warnings;
    }

    /** 这条记录会覆盖库里的姓名（条件和 mergePerson 一致），并且姓名确实不同。 */
    private static boolean nameChanged(PositioningMapper.PersonNow before, PositioningRow.Person r,
                                       LocalDateTime src) {
        boolean applies = before.fromRyxx() == 0 || !before.srcDataTime().isAfter(src);
        return applies && before.personName() != null && r.personName() != null
                && !before.personName().equals(r.personName());
    }

    /** 只记一行处理记录（失败、重复、未识别类型），不写业务表。 */
    @Transactional
    public void record(IngestFileEntry entry, List<RecordIssue> issues) {
        mapper.insertFile(entry);
        issues.stream().limit(MAX_ISSUES_PER_FILE).forEach(i -> mapper.insertIssue(entry.getId(), i));
    }

    /** RYSS 是全量快照：更新每人当前状态，补录人员，记每日出入井和这一时刻的井下人数。 */
    private void writeSnapshot(List<PositioningRow.PersonState> rows, LocalDateTime src) {
        LocalDate statDate = src.toLocalDate();
        int inWell = 0;
        int out = 0;
        for (PositioningRow.PersonState r : rows) {
            mapper.mergePersonState(r, src);
            mapper.insertPersonIfMissing(r.cardCode(), r.personName(), src);
            if (r.inOutFlag() == 1) {
                inWell++;
                mapper.mergePresenceIn(statDate, r.cardCode(), r.inTime(), src);
            } else if (r.inOutFlag() == 2) {
                out++;
                if (r.outTime() != null) {
                    LocalDate from = r.inTime() != null && r.inTime().toLocalDate().isBefore(statDate)
                            ? r.inTime().toLocalDate() : statDate;
                    mapper.updatePresenceOut(r.cardCode(), from, statDate, r.outTime());
                }
            }
        }
        mapper.mergeHeadcount(src, inWell, out, rows.size());
    }

    /**
     * 处理记录里与解析结果无关的信息。
     *
     * @param backupPath 备份或失败目录里的位置，如 backup/jxry/620823009203/20260923/xxx.txt
     */
    public record FileFacts(String fileName, String mineCode, String sha256, Long sizeBytes,
                            LocalDateTime receivedAt, String backupPath) {

        IngestFileEntry toEntry(String fileType, LocalDateTime headerTime, IngestStatus status,
                                Integer recordCount, Integer errorCount, String errorMsg) {
            return new IngestFileEntry(fileName, fileType, mineCode, headerTime, sha256, sizeBytes, status,
                    recordCount, errorCount, errorMsg, receivedAt, backupPath);
        }
    }
}
