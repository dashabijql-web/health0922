package com.xzkj.hv2.health;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HealthSummaryMapper {

    /** 按 [from, to) 的体征流水重算 statDate 这一天的日汇总，返回写入的行数。 */
    int mergeDay(@Param("statDate") LocalDate statDate, @Param("from") LocalDateTime from,
                 @Param("to") LocalDateTime to);
}
