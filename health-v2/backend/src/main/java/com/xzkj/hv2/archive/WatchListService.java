package com.xzkj.hv2.archive;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xzkj.hv2.archive.ArchiveViews.WatchListItem;
import com.xzkj.hv2.auth.SessionGateway;
import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.common.oplog.OperationAction;
import com.xzkj.hv2.common.oplog.OperationLogService;

/**
 * 重点监护、今日关注名单（docs/05 第八节）：只靠人工加入和移除，系统不自动加人。
 * "今日关注"当天有效，过了这天自动失效（按 EXPIRE_DATE 判断，不用定时任务删）。
 * 加入、移除和操作日志在同一个事务里；日志对象是人（TARGET_TYPE = PERSON，编号是卡编码）。
 */
@Service
public class WatchListService {

    static final int MAX_NOTE_LENGTH = 200;

    private final ArchiveMapper mapper;
    private final OperationLogService oplog;
    private final SessionGateway session;
    private final Clock clock;

    public WatchListService(ArchiveMapper mapper, OperationLogService oplog, SessionGateway session, Clock clock) {
        this.mapper = mapper;
        this.oplog = oplog;
        this.session = session;
        this.clock = clock;
    }

    /** 操作日志里名单一条的样子 */
    record Entry(long id, String type, String note, LocalDate expireDate) {

        static Entry of(ArchiveMapper.ListItemRow r) {
            return new Entry(r.id(), r.listType(), r.note(), r.expireDate());
        }
    }

    /** @param type KEY 重点监护 / TODAY 今日关注（只列今天还有效的） */
    public List<WatchListItem> list(String type) {
        return mapper.watchList(type, LocalDate.now(clock)).stream().map(WatchListService::toItem).toList();
    }

    /**
     * 加入名单。已经在名单里（还有效）时原样返回，不重复记日志；过期的今日关注会被覆盖成今天的。
     */
    @Transactional
    public WatchListItem add(String cardCode, String type, String rawNote) {
        String note = rawNote == null || rawNote.isBlank() ? null : rawNote.strip();
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw BizException.badRequest("备注最长 " + MAX_NOTE_LENGTH + " 个字");
        }
        if (!mapper.personExists(cardCode)) {
            throw ArchiveService.notFound();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        ArchiveMapper.ListItemRow current = mapper.lockWatchListItem(cardCode, type);
        if (current != null && effective(current, today)) {
            return toItem(current);
        }
        try {
            mapper.mergeWatchList(cardCode, type, note, "TODAY".equals(type) ? today : null,
                    session.currentUser().username(), now);
        } catch (DuplicateKeyException e) {
            // 两人同时第一次加入同一个人：先提交的已经加进去了，这一次就当已在名单里
            ArchiveMapper.ListItemRow added = mapper.lockWatchListItem(cardCode, type);
            if (added != null) {
                return toItem(added);
            }
            throw e;
        }
        ArchiveMapper.ListItemRow added = mapper.lockWatchListItem(cardCode, type);
        oplog.record(OperationAction.WATCH_LIST_ADD, ArchiveService.TARGET, cardCode, null, Entry.of(added));
        return toItem(added);
    }

    /** 移出名单。 */
    @Transactional
    public void remove(long id) {
        ArchiveMapper.ListItemRow current = mapper.lockWatchListById(id);
        if (current == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "名单里没有这一条，可能已被其他人移出，请刷新");
        }
        mapper.deleteWatchList(id);
        oplog.record(OperationAction.WATCH_LIST_REMOVE, ArchiveService.TARGET, current.cardCode(),
                Entry.of(current), null);
    }

    private static boolean effective(ArchiveMapper.ListItemRow r, LocalDate today) {
        return "KEY".equals(r.listType()) || (r.expireDate() != null && !r.expireDate().isBefore(today));
    }

    private static WatchListItem toItem(ArchiveMapper.ListItemRow r) {
        return new WatchListItem(r.id(), r.cardCode(), r.personName(), r.dept(), r.listType(), r.note(),
                r.expireDate(), r.addedBy(), r.addedAt());
    }
}
