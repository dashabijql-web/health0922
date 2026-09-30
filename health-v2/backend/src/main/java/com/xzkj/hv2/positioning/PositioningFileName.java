package com.xzkj.hv2.positioning;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 定位文件名 {@code <煤矿编码12位>_<类型>_<上传时间14位>.txt}，如 {@code 620823009203_RYSS_20260923101319.txt}
 * （docs/02 第二节第 1 步）。
 */
public record PositioningFileName(String fileName, String mineCode, String type, String uploadTime) {

    private static final Pattern PATTERN = Pattern.compile("^(\\d{12})_([A-Z]+)_(\\d{14})\\.txt$");

    /** 超时 / 超员 / 厂家自加：收到直接删除，不备份（docs/02 第一节） */
    private static final Set<String> DISCARDED = Set.of("RYCS", "RYCY", "RYYJCS");

    /** 文件名不合法时返回 null。 */
    public static PositioningFileName parse(String fileName) {
        Matcher m = PATTERN.matcher(fileName);
        if (!m.matches()) {
            return null;
        }
        return new PositioningFileName(fileName, m.group(1), m.group(2), m.group(3));
    }

    public boolean discarded() {
        return DISCARDED.contains(type);
    }

    /** 会解析入库的类型；其他类型（如 RYXZ、RYQJ）只备份，记为 UNKNOWN。 */
    public PositioningFileType knownType() {
        return PositioningFileType.of(type);
    }

    /** 上传时间里的日期 yyyyMMdd，用作备份子目录名。只含数字，可以安全地拼进路径。 */
    public String uploadDate() {
        return uploadTime.substring(0, 8);
    }
}
