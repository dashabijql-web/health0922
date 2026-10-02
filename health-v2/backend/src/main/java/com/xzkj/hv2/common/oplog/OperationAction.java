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
    WATCH_LIST_REMOVE,
    /** 部署工具 hv2-ops 改密码（tools/ops，docs/11），后端自己不写 */
    USER_PASSWORD_SET,
    /** 部署工具 hv2-ops 导入手表绑定清单（tools/ops，docs/11），后端自己不写 */
    DEVICE_BIND
}
