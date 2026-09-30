package com.xzkj.hv2.positioning;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按国家煤监局 42 号文附件2 的格式解析定位文件（docs/02 第三节）。纯计算，不读写文件和数据库。
 * <p>
 * 格式：没有换行符，{@code ~} 结束一条记录，{@code ||} 结束整个文件；第一段是文件头，之后每段一条记录；
 * 字段用英文分号分隔，空字段也保留分号。
 */
public final class PositioningParser {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    /** POS_INGEST_ERROR.RAW_TEXT 的长度上限 */
    private static final int RAW_TEXT_MAX = 1000;

    private static final String NOT_SET = "未设置";

    private PositioningParser() {
    }

    /** 文件是否完整：去掉末尾空白后以 {@code ||} 结尾。 */
    public static boolean isComplete(byte[] content) {
        int end = content.length;
        while (end > 0 && isAsciiSpace(content[end - 1])) {
            end--;
        }
        return end >= 2 && content[end - 1] == '|' && content[end - 2] == '|';
    }

    /**
     * @param expectedMineCode 文件名里的煤矿编码，文件头里的必须与它一致
     * @throws PositioningFormatException 整个文件不能用
     */
    public static ParsedFile parse(PositioningFileType type, String expectedMineCode, byte[] content)
            throws PositioningFormatException {
        String text = decodeUtf8(content);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        text = text.strip();
        if (!text.endsWith("||")) {
            throw new PositioningFormatException("文件不完整：末尾没有 ||");
        }
        String[] segments = text.substring(0, text.length() - 2).split("~", -1);

        LocalDateTime headerTime = parseHeader(type, expectedMineCode, clean(segments[0]));

        Map<String, PositioningRow> rows = new LinkedHashMap<>();
        Map<String, Integer> lineOfKey = new LinkedHashMap<>();
        List<RecordIssue> issues = new ArrayList<>();
        int lineNo = 0;
        for (int i = 1; i < segments.length; i++) {
            String raw = clean(segments[i]);
            if (raw.isEmpty()) {
                continue;
            }
            lineNo++;
            PositioningRow row;
            try {
                row = parseRecord(type, raw);
            } catch (RecordException e) {
                issues.add(RecordIssue.error(lineNo, truncate(raw), e.getMessage()));
                continue;
            }
            Integer previous = lineOfKey.put(row.key(), lineNo);
            if (previous != null) {
                rows.remove(row.key());
                issues.add(RecordIssue.warn(lineNo, truncate(raw),
                        duplicateLabel(type) + "与第 " + previous + " 条重复，以本条为准"));
            }
            rows.put(row.key(), row);
        }
        return new ParsedFile(type, headerTime, lineNo, List.copyOf(rows.values()), List.copyOf(issues));
    }

    private static LocalDateTime parseHeader(PositioningFileType type, String expectedMineCode, String header)
            throws PositioningFormatException {
        if (header.indexOf('；') >= 0) {
            throw new PositioningFormatException("文件头含全角分号");
        }
        String[] f = splitFields(header);
        if (f.length < type.headerCount()) {
            throw new PositioningFormatException(
                    "文件头只有 " + f.length + " 段，" + type + " 应为 " + type.headerCount() + " 段");
        }
        if (!f[0].equals(expectedMineCode)) {
            throw new PositioningFormatException("文件头的煤矿编码与文件名不符");
        }
        String time = f[type.headerCount() - 1];
        try {
            return LocalDateTime.parse(time, TIME);
        } catch (DateTimeParseException e) {
            throw new PositioningFormatException("文件头的数据上传时间格式不对");
        }
    }

    private static PositioningRow parseRecord(PositioningFileType type, String raw) {
        if (raw.indexOf('；') >= 0) {
            throw new RecordException("含全角分号");
        }
        String[] f = splitFields(raw);
        if (f.length != type.fieldCount()) {
            throw new RecordException("字段数 " + f.length + "，应为 " + type.fieldCount());
        }
        return switch (type) {
            case RYQY -> new PositioningRow.Area(
                    code(f[1], 16, "区域编码"),
                    text(f[0], 10, "区域类型"),
                    integer(f[2], 999_999, "核定人数"),
                    text(f[3], 100, "区域名称"));
            case RYJZ -> new PositioningRow.StationName(
                    code(f[0], 22, "基站编码"),
                    text(f[1], 200, "基站名称"));
            case JZSS -> new PositioningRow.StationStatus(
                    code(f[0], 22, "基站编码"),
                    status(f[1], "运行状态"),
                    status(f[2], "供电状态"),
                    time(f[3], "数据时间"));
            case RYXX -> new PositioningRow.Person(
                    code(f[0], 17, "人员卡编码"),
                    text(f[1], 50, "姓名"),
                    notSet(text(f[2], 50, "工种")),
                    notSet(text(f[3], 50, "职务")),
                    text(f[4], 100, "队组/部门"),
                    yesNo(f[7], "是否矿领导"),
                    yesNo(f[8], "是否特种人员"));
            case RYSS -> new PositioningRow.PersonState(
                    code(f[0], 17, "人员卡编码"),
                    text(f[1], 50, "姓名"),
                    inOutFlag(f[2]),
                    time(f[3], "入井时刻"),
                    time(f[4], "出井时刻"),
                    optionalCode(f[5], 16, "区域编码"),
                    time(f[6], "进入当前区域时刻"),
                    optionalCode(f[7], 22, "基站编码"),
                    time(f[8], "进入当前基站时刻"),
                    text(f[9], 20, "劳动组织方式"),
                    distance(f[10]),
                    text(f[11], 10, "人员工作状态"),
                    yesNo(f[12], "是否矿领导"),
                    yesNo(f[13], "是否特种人员"));
        };
    }

    // ---- 字段 ----

    /** 必填的数字编码，位数固定 */
    private static String code(String v, int length, String name) {
        if (v.isEmpty()) {
            throw new RecordException(name + "为空");
        }
        return optionalCode(v, length, name);
    }

    private static String optionalCode(String v, int length, String name) {
        if (v.isEmpty()) {
            return null;
        }
        if (v.length() != length || !v.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new RecordException(name + "应为 " + length + " 位数字");
        }
        return v;
    }

    private static String text(String v, int maxLength, String name) {
        if (v.isEmpty()) {
            return null;
        }
        if (v.codePointCount(0, v.length()) > maxLength) {
            throw new RecordException(name + "超过 " + maxLength + " 个字");
        }
        return v;
    }

    private static String notSet(String v) {
        return NOT_SET.equals(v) ? null : v;
    }

    private static LocalDateTime time(String v, String name) {
        if (v.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(v, TIME);
        } catch (DateTimeParseException e) {
            throw new RecordException(name + "格式不对");
        }
    }

    private static Integer integer(String v, int max, String name) {
        if (v.isEmpty()) {
            return null;
        }
        try {
            int n = Integer.parseInt(v);
            if (n < 0 || n > max) {
                throw new RecordException(name + "超出范围");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new RecordException(name + "不是整数");
        }
    }

    private static Integer yesNo(String v, String name) {
        return switch (v) {
            case "" -> null;
            case "0" -> 0;
            case "1" -> 1;
            default -> throw new RecordException(name + "应为 0 或 1");
        };
    }

    /** 基站运行状态 / 供电状态：0、1、2、9 */
    private static Integer status(String v, String name) {
        return switch (v) {
            case "" -> null;
            case "0", "1", "2", "9" -> Integer.parseInt(v);
            default -> throw new RecordException(name + "应为 0、1、2 或 9");
        };
    }

    private static int inOutFlag(String v) {
        return switch (v) {
            case "0", "1", "2" -> Integer.parseInt(v);
            case "" -> throw new RecordException("出入井标志为空");
            default -> throw new RecordException("出入井标志应为 0、1 或 2");
        };
    }

    /** 距离基站距离（米，有正负），库里是 NUMBER(8,2) */
    private static BigDecimal distance(String v) {
        if (v.isEmpty()) {
            return null;
        }
        try {
            BigDecimal d = new BigDecimal(v);
            if (d.abs().compareTo(BigDecimal.valueOf(999_999)) > 0) {
                throw new RecordException("距离基站距离超出范围");
            }
            return d;
        } catch (NumberFormatException e) {
            throw new RecordException("距离基站距离不是数字");
        }
    }

    // ---- 工具 ----

    private static String decodeUtf8(byte[] content) throws PositioningFormatException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new PositioningFormatException("不是 UTF-8 编码");
        }
    }

    /** 去掉混进来的换行和前后空白 */
    private static String clean(String segment) {
        return segment.replace("\r", "").replace("\n", "").strip();
    }

    /** 保留空字段（split 的 limit 为 -1），每段去掉前后空白 */
    private static String[] splitFields(String s) {
        String[] f = s.split(";", -1);
        for (int i = 0; i < f.length; i++) {
            f[i] = f[i].strip();
        }
        return f;
    }

    private static String truncate(String raw) {
        return raw.length() <= RAW_TEXT_MAX ? raw : raw.substring(0, RAW_TEXT_MAX);
    }

    private static String duplicateLabel(PositioningFileType type) {
        return switch (type) {
            case RYQY -> "区域编码";
            case RYJZ, JZSS -> "基站编码";
            case RYXX, RYSS -> "人员卡编码";
        };
    }

    private static boolean isAsciiSpace(byte b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n';
    }

    /** 单条记录有问题：跳过这条，文件照常处理 */
    private static final class RecordException extends RuntimeException {
        RecordException(String message) {
            super(message, null, false, false);
        }
    }
}
