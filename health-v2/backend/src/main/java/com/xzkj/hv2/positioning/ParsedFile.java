package com.xzkj.hv2.positioning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 解析好的一个定位文件。
 *
 * @param headerTime  文件头里的"数据上传时间"，判断新旧的依据
 * @param recordCount 文件里的记录条数（不含文件头）
 * @param rows        没出错的记录；同一键出现多次时只留靠后的一条，顺序按文件里最后出现的位置
 * @param issues      出错（被跳过）和需要核对的记录
 * @param lineOfKey   rows 里每条记录是文件里的第几条（键 → 行号），入库时发现要核对的情况用它记警告
 */
public record ParsedFile(PositioningFileType type, LocalDateTime headerTime, int recordCount,
                         List<PositioningRow> rows, List<RecordIssue> issues, Map<String, Integer> lineOfKey) {

    public int errorCount() {
        return (int) issues.stream().filter(i -> i.kind() == RecordIssue.Kind.ERROR).count();
    }

    public <T extends PositioningRow> List<T> rowsOf(Class<T> rowType) {
        return rows.stream().map(rowType::cast).toList();
    }

    /** 某条记录在文件里是第几条；找不到时为 0。 */
    public int lineOf(PositioningRow row) {
        return lineOfKey.getOrDefault(row.key(), 0);
    }
}
