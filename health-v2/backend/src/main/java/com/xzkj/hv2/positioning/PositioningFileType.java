package com.xzkj.hv2.positioning;

/** 会解析入库的五类定位文件（docs/02 第一节、第三节）。 */
public enum PositioningFileType {

    /** 区域 */
    RYQY(4, 3),
    /** 基站（只取名称） */
    RYJZ(6, 3),
    /** 人员信息；文件头 8 段，上传时间在最后一段 */
    RYXX(9, 8),
    /** 人员实时位置和出入井（全量快照） */
    RYSS(15, 3),
    /** 基站运行状态 */
    JZSS(4, 3);

    /** 每条记录的段数 */
    private final int fieldCount;
    /** 文件头的段数 */
    private final int headerCount;

    PositioningFileType(int fieldCount, int headerCount) {
        this.fieldCount = fieldCount;
        this.headerCount = headerCount;
    }

    public int fieldCount() {
        return fieldCount;
    }

    public int headerCount() {
        return headerCount;
    }

    /** 不是这五类时返回 null。 */
    public static PositioningFileType of(String type) {
        for (PositioningFileType t : values()) {
            if (t.name().equals(type)) {
                return t;
            }
        }
        return null;
    }
}
