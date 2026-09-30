package com.xzkj.hv2.positioning;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class PositioningStatusService {

    private final PositioningMapper mapper;
    private final PositioningProperties props;
    private final Clock clock;

    public PositioningStatusService(PositioningMapper mapper, PositioningProperties props, Clock clock) {
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    public PositioningStatus status() {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);

        Map<String, LocalDateTime> effective = new LinkedHashMap<>();
        for (PositioningStatus.TypeTime t : mapper.latestEffectivePerType()) {
            effective.put(t.fileType(), t.headerTime());
        }
        Map<String, PositioningStatus.LastFile> last = new LinkedHashMap<>();
        for (PositioningStatus.LastFile f : mapper.latestFilePerType()) {
            last.put(f.fileType(), f);
        }

        List<PositioningStatus.TypeStatus> types = new ArrayList<>();
        for (PositioningFileType t : PositioningFileType.values()) {
            types.add(new PositioningStatus.TypeStatus(t.name(), effective.get(t.name()), last.remove(t.name())));
        }
        // 收到过的未识别类型（如 RYQJ）也列出来，方便发现厂家多发了什么
        last.values().forEach(f -> types.add(new PositioningStatus.TypeStatus(f.fileType(), null, f)));

        LocalDateTime lastRyss = effective.get(PositioningFileType.RYSS.name());
        boolean stale = lastRyss == null || lastRyss.plusMinutes(props.staleMinutes()).isBefore(now);
        Integer inWell = lastRyss == null ? null : mapper.countInWell();

        return new PositioningStatus(now, props.staleMinutes(), stale, lastRyss, inWell,
                mapper.countFailedSince(now.minusHours(24)), types);
    }
}
