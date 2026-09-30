package com.xzkj.hv2.common.oplog;

/** 写入 SYS_OPERATION_LOG 的一行；ID、CREATED_AT 由数据库生成。 */
public record OperationLogEntry(
        Long userId,
        String username,
        String action,
        String targetType,
        String targetId,
        String beforeJson,
        String afterJson) {
}
