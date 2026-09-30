package com.xzkj.hv2.common.oplog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xzkj.hv2.auth.CurrentUser;
import com.xzkj.hv2.auth.SessionGateway;

import tools.jackson.databind.ObjectMapper;

/**
 * 操作日志组件：记下"谁、何时、对哪个对象、做了什么、改动前后"（docs/04 SYS_OPERATION_LOG）。
 * <p>
 * 必须在业务改动的同一个事务里调用（{@link Propagation#MANDATORY}），
 * 保证改动和日志要么都成功、要么都作废，不会出现"改了但没记"。
 */
@Service
public class OperationLogService {

    private final OperationLogMapper mapper;
    private final SessionGateway session;
    private final ObjectMapper objectMapper;

    public OperationLogService(OperationLogMapper mapper, SessionGateway session, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.session = session;
        this.objectMapper = objectMapper;
    }

    /**
     * @param before 改动前的对象，新增时传 null
     * @param after  改动后的对象，删除时传 null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(OperationAction action, String targetType, String targetId, Object before, Object after) {
        CurrentUser user = session.currentUser();
        mapper.insert(new OperationLogEntry(
                user.id(), user.username(), action.name(), targetType, targetId,
                toJson(before), toJson(after)));
    }

    private String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }
}
