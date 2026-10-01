package com.xzkj.hv2.map;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xzkj.hv2.auth.SessionGateway;
import com.xzkj.hv2.common.exception.BizException;
import com.xzkj.hv2.common.oplog.OperationAction;
import com.xzkj.hv2.common.oplog.OperationLogService;
import com.xzkj.hv2.map.MapViews.Station;

/**
 * 基站摆放：新增、移动、改名、删除（docs/06 第五节）。
 * <p>
 * 每次改动和它的操作日志在同一个事务里，日志写失败则改动一并回滚。
 * 并发：先锁住这一行（SELECT … FOR UPDATE）再比对版本号，两人同时改同一基站时后提交的收到 409；
 * 两人同时第一次摆放（都传版本 0）时，后提交的插入撞主键，同样 409。
 */
@Service
public class StationMarkService {

    static final int MAX_NAME_LENGTH = 100;
    private static final String TARGET = "STATION";

    private final MapMapper mapper;
    private final MapService maps;
    private final MapProperties props;
    private final OperationLogService oplog;
    private final SessionGateway session;
    private final Clock clock;

    public StationMarkService(MapMapper mapper, MapService maps, MapProperties props, OperationLogService oplog,
                              SessionGateway session, Clock clock) {
        this.mapper = mapper;
        this.maps = maps;
        this.props = props;
        this.oplog = oplog;
        this.session = session;
        this.clock = clock;
    }

    /** 日志里记的摆放内容：改动前后各一份。坐标统一写 3 位小数（库里读出来的会去掉末尾的 0） */
    record Mark(BigDecimal x, BigDecimal y, String displayName) {

        static Mark of(BigDecimal x, BigDecimal y, String displayName) {
            return new Mark(x.setScale(3, RoundingMode.HALF_UP), y.setScale(3, RoundingMode.HALF_UP), displayName);
        }
    }

    /**
     * 新增（version = 0）或修改（version = 读到的版本号）。
     *
     * @return 改完后的基站
     */
    @Transactional
    public Station save(String stationCode, BigDecimal rawX, BigDecimal rawY, String rawName, long version) {
        if (!mapper.stationExists(stationCode)) {
            throw new BizException(HttpStatus.NOT_FOUND, "没有这个基站");
        }
        BigDecimal x = rawX.setScale(3, RoundingMode.HALF_UP);
        BigDecimal y = rawY.setScale(3, RoundingMode.HALF_UP);
        if (!props.extent().contains(x, y)) {
            throw BizException.badRequest("位置超出地图范围");
        }
        String name = rawName == null || rawName.isBlank() ? null : rawName.strip();
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            throw BizException.badRequest("名称最长 " + MAX_NAME_LENGTH + " 个字");
        }
        String username = session.currentUser().username();
        LocalDateTime now = LocalDateTime.now(clock);
        Mark after = Mark.of(x, y, name);

        MapMapper.MarkRow current = mapper.lockMark(stationCode);
        if (version == 0) {
            if (current != null) {
                throw conflict(stationCode);
            }
            try {
                mapper.insertMark(stationCode, x, y, name, username, now);
            } catch (DuplicateKeyException e) {
                // 两人同时第一次摆放：先提交的插进去了，这一次撞主键
                throw conflict(stationCode);
            }
            oplog.record(OperationAction.STATION_PLACE, TARGET, stationCode, null, after);
        } else {
            if (current == null || current.version() != version) {
                throw conflict(stationCode);
            }
            Mark before = Mark.of(current.x(), current.y(), current.displayName());
            boolean moved = current.x().compareTo(x) != 0 || current.y().compareTo(y) != 0;
            boolean renamed = !Objects.equals(current.displayName(), name);
            if (!moved && !renamed) {
                return maps.station(stationCode);
            }
            if (mapper.updateMark(stationCode, x, y, name, username, now, version) != 1) {
                throw conflict(stationCode);
            }
            // 同时挪了位置又改了名，两件事各记一条
            if (moved) {
                oplog.record(OperationAction.STATION_MOVE, TARGET, stationCode, before, after);
            }
            if (renamed) {
                oplog.record(OperationAction.STATION_RENAME, TARGET, stationCode, before, after);
            }
        }
        return maps.station(stationCode);
    }

    /** 删除摆放：基站回到"未摆放"。 */
    @Transactional
    public void delete(String stationCode, long version) {
        MapMapper.MarkRow current = mapper.lockMark(stationCode);
        if (current == null && !mapper.stationExists(stationCode)) {
            throw new BizException(HttpStatus.NOT_FOUND, "没有这个基站");
        }
        if (current == null || current.version() != version) {
            throw conflict(stationCode);
        }
        if (mapper.deleteMark(stationCode, version) != 1) {
            throw conflict(stationCode);
        }
        oplog.record(OperationAction.STATION_DELETE, TARGET, stationCode,
                Mark.of(current.x(), current.y(), current.displayName()), null);
    }

    private BizException conflict(String stationCode) {
        String who = mapper.lastStationActor(stationCode);
        return BizException.conflict("这个基站刚刚被 " + (who != null ? who : "其他人") + " 修改，请刷新");
    }
}
