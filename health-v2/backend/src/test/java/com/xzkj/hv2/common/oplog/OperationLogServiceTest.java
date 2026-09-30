package com.xzkj.hv2.common.oplog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xzkj.hv2.auth.CurrentUser;
import com.xzkj.hv2.auth.SessionGateway;

import tools.jackson.databind.json.JsonMapper;

class OperationLogServiceTest {

    private final OperationLogMapper mapper = mock(OperationLogMapper.class);
    private final SessionGateway session = mock(SessionGateway.class);
    private final OperationLogService service =
            new OperationLogService(mapper, session, JsonMapper.builder().build());

    @Test
    void recordsWhoWhatAndBeforeAfter() {
        when(session.currentUser()).thenReturn(new CurrentUser(7, "admin"));

        service.record(OperationAction.STATION_MOVE, "STATION", "6208230092030008000012",
                Map.of("x", 1.5), Map.of("x", 2.5));

        ArgumentCaptor<OperationLogEntry> captor = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(mapper).insert(captor.capture());
        OperationLogEntry entry = captor.getValue();
        assertThat(entry.userId()).isEqualTo(7L);
        assertThat(entry.username()).isEqualTo("admin");
        assertThat(entry.action()).isEqualTo("STATION_MOVE");
        assertThat(entry.targetType()).isEqualTo("STATION");
        assertThat(entry.targetId()).isEqualTo("6208230092030008000012");
        assertThat(entry.beforeJson()).isEqualTo("{\"x\":1.5}");
        assertThat(entry.afterJson()).isEqualTo("{\"x\":2.5}");
    }

    @Test
    void createHasNoBeforeAndDeleteHasNoAfter() {
        when(session.currentUser()).thenReturn(new CurrentUser(1, "admin"));

        service.record(OperationAction.STATION_PLACE, "STATION", "A", null, Map.of("x", 1));
        service.record(OperationAction.STATION_DELETE, "STATION", "A", Map.of("x", 1), null);

        ArgumentCaptor<OperationLogEntry> captor = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).beforeJson()).isNull();
        assertThat(captor.getAllValues().get(1).afterJson()).isNull();
    }

    @Test
    void mustRunInsideCallersTransaction() throws Exception {
        Method record = OperationLogService.class.getMethod("record", OperationAction.class,
                String.class, String.class, Object.class, Object.class);
        assertThat(record.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
