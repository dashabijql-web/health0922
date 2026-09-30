package com.xzkj.hv2.common.oplog;

import org.apache.ibatis.annotations.Mapper;

/** 操作日志只增不改：这里只有 INSERT，数据库触发器拒绝 UPDATE/DELETE。 */
@Mapper
public interface OperationLogMapper {

    int insert(OperationLogEntry entry);
}
