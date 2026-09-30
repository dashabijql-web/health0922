package com.xzkj.hv2.common.oplog;

/** 操作日志的动作（docs/04 SYS_OPERATION_LOG.ACTION）。 */
public enum OperationAction {
    STATION_PLACE,
    STATION_MOVE,
    STATION_RENAME,
    STATION_DELETE,
    PERSON_AGE_SET,
    ALERT_RULE_SET,
    JOB_GROUP_SET,
    JOB_KIND_GROUP_SET,
    WATCH_LIST_ADD,
    WATCH_LIST_REMOVE
}
