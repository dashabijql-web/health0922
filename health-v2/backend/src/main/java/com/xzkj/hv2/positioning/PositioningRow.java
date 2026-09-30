package com.xzkj.hv2.positioning;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 定位文件里一条解析好的记录。各类型的字段含义见 docs/02 第三节。 */
public sealed interface PositioningRow {

    /** 同一文件里用来判断"重复"的键，重复时以靠后的一条为准。 */
    String key();

    /** RYQY：区域 */
    record Area(String areaCode, String areaType, Integer quota, String areaName) implements PositioningRow {
        @Override
        public String key() {
            return areaCode;
        }
    }

    /** RYJZ：基站名称（坐标和位置注释不用） */
    record StationName(String stationCode, String stationName) implements PositioningRow {
        @Override
        public String key() {
            return stationCode;
        }
    }

    /** JZSS：基站运行状态 */
    record StationStatus(String stationCode, Integer runStatus, Integer powerStatus, LocalDateTime statusTime)
            implements PositioningRow {
        @Override
        public String key() {
            return stationCode;
        }
    }

    /** RYXX：人员信息（出生年月、学历不用） */
    record Person(String cardCode, String personName, String jobKind, String jobTitle, String dept,
                  Integer isLeader, Integer isSpecial) implements PositioningRow {
        @Override
        public String key() {
            return cardCode;
        }
    }

    /** RYSS：人员实时位置和出入井（行进轨迹不用） */
    record PersonState(String cardCode, String personName, int inOutFlag, LocalDateTime inTime,
                       LocalDateTime outTime, String areaCode, LocalDateTime areaEnterTime,
                       String stationCode, LocalDateTime stationEnterTime, String shiftMode,
                       BigDecimal distanceM, String workStatus, Integer isLeader, Integer isSpecial)
            implements PositioningRow {
        @Override
        public String key() {
            return cardCode;
        }
    }
}
