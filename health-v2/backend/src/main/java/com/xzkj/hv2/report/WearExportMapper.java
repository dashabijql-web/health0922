package com.xzkj.hv2.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WearExportMapper {

    List<WearRow> wearRows(@Param("statDate") LocalDate statDate, @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    /** @param uploaded 那天有没有体征记录 */
    record WearRow(String cardCode, String personName, String dept, boolean uploaded) {
    }
}
