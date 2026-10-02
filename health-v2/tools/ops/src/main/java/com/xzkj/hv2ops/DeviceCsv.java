package com.xzkj.hv2ops;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 手表绑定清单（docs/11"手表绑定清单"）：CSV，第一行是表头，至少有 IMEI、卡编码两列，可选"型号"，其他列忽略。
 * 编码：带 BOM 或能按 UTF-8 读通的按 UTF-8，否则按 GBK（Windows 上 Excel"另存为 CSV"默认是 GBK）。
 * 卡编码为空表示只登记、不绑定。只解析和检查格式，不连数据库。
 */
final class DeviceCsv {

    private static final Pattern IMEI = Pattern.compile("\\d{15}");
    private static final Pattern CARD = Pattern.compile("\\d{17}");
    /** Excel 把长数字显示成 8.69E+14 这样，另存为 CSV 后就丢了后几位 */
    private static final Pattern SCIENTIFIC = Pattern.compile("(?i)\\d(\\.\\d+)?e\\+?\\d+");
    private static final int MODEL_MAX = 50;

    record Row(int line, String imei, String card, String model) {
    }

    record Parsed(String charset, List<Row> rows, List<String> errors) {
    }

    private DeviceCsv() {
    }

    static Parsed parse(byte[] bytes) {
        String charset;
        String text;
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xEF && (bytes[1] & 0xff) == 0xBB && (bytes[2] & 0xff) == 0xBF) {
            charset = "UTF-8（带 BOM）";
            text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        } else {
            String utf8 = strictDecode(bytes, StandardCharsets.UTF_8);
            if (utf8 != null) {
                charset = "UTF-8";
                text = utf8;
            } else {
                charset = "GBK";
                text = new String(bytes, Charset.forName("GBK"));
            }
        }

        List<String> errors = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        String[] lines = text.split("\r\n|\n|\r", -1);
        int headerLine = -1;
        int imeiCol = -1;
        int cardCol = -1;
        int modelCol = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            if (headerLine < 0) {
                headerLine = i + 1;
                List<String> header = fields(lines[i]);
                for (int c = 0; c < header.size(); c++) {
                    String h = header.get(c).trim().toUpperCase(Locale.ROOT);
                    switch (h) {
                        case "IMEI", "设备号" -> imeiCol = c;
                        case "卡编码", "人员卡编码", "CARD_CODE" -> cardCol = c;
                        case "型号", "MODEL" -> modelCol = c;
                        default -> { }
                    }
                }
                if (imeiCol < 0 || cardCol < 0) {
                    errors.add("第 " + headerLine + " 行（表头）要有 IMEI 和 卡编码 两列");
                    return new Parsed(charset, rows, errors);
                }
                continue;
            }
            int line = i + 1;
            List<String> f = fields(lines[i]);
            String imei = get(f, imeiCol);
            String card = get(f, cardCol);
            String model = modelCol < 0 ? "" : get(f, modelCol);
            int before = errors.size();
            checkNumber(errors, line, "IMEI", imei, IMEI, 15, false);
            checkNumber(errors, line, "卡编码", card, CARD, 17, true);
            if (model.length() > MODEL_MAX) {
                errors.add("第 " + line + " 行：型号超过 " + MODEL_MAX + " 个字");
            }
            if (errors.size() == before) {
                rows.add(new Row(line, imei, card.isEmpty() ? null : card, model.isEmpty() ? null : model));
            }
        }
        if (headerLine < 0) {
            errors.add("文件是空的");
        }
        duplicates(rows, errors);
        return new Parsed(charset, rows, errors);
    }

    private static void checkNumber(List<String> errors, int line, String label, String value, Pattern p,
                                    int digits, boolean optional) {
        if (value.isEmpty()) {
            if (!optional) {
                errors.add("第 " + line + " 行：" + label + " 为空");
            }
            return;
        }
        if (SCIENTIFIC.matcher(value).matches()) {
            errors.add("第 " + line + " 行：" + label + " 是 " + value + "，被 Excel 改成了科学计数法、丢了后几位；"
                    + "请在 Excel 里把这一列设成\"文本\"，重新填好再另存");
        } else if (!p.matcher(value).matches()) {
            errors.add("第 " + line + " 行：" + label + " 应为 " + digits + " 位数字，实际是 " + value);
        }
    }

    private static void duplicates(List<Row> rows, List<String> errors) {
        Map<String, Integer> imeis = new HashMap<>();
        Map<String, Integer> cards = new HashMap<>();
        for (Row r : rows) {
            Integer prev = imeis.putIfAbsent(r.imei(), r.line());
            if (prev != null) {
                errors.add("第 " + r.line() + " 行：IMEI " + r.imei() + " 和第 " + prev + " 行重复");
            }
            if (r.card() != null) {
                prev = cards.putIfAbsent(r.card(), r.line());
                if (prev != null) {
                    errors.add("第 " + r.line() + " 行：卡编码 " + r.card() + " 和第 " + prev + " 行重复（一个人只能绑一块表）");
                }
            }
        }
    }

    private static String get(List<String> f, int col) {
        return col < f.size() ? f.get(col).trim() : "";
    }

    /** 一行 CSV：逗号分隔，双引号里的逗号不算，两个双引号表示一个双引号 */
    static List<String> fields(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String strictDecode(byte[] bytes, Charset cs) {
        try {
            return cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
