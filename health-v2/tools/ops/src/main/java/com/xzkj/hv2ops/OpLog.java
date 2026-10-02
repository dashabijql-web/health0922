package com.xzkj.hv2ops;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** 往 SYS_OPERATION_LOG 记一条（docs/04）：部署工具做的改动记在 SYSTEM 名下，USER_ID 为空。 */
final class OpLog {

    private OpLog() {
    }

    static void write(Connection c, String action, String targetType, String targetId,
                      String beforeJson, String afterJson) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO SYS_OPERATION_LOG (USER_ID, USERNAME, ACTION, TARGET_TYPE, TARGET_ID, BEFORE_JSON, AFTER_JSON)
                VALUES (NULL, 'SYSTEM', ?, ?, ?, ?, ?)""")) {
            ps.setString(1, action);
            ps.setString(2, targetType);
            ps.setString(3, targetId);
            ps.setString(4, beforeJson);
            ps.setString(5, afterJson);
            ps.executeUpdate();
        }
    }

    /** 最简单的 JSON 字符串转义（只用于登录名、卡编码这类短文字） */
    static String json(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                default -> {
                    if (ch < 0x20) {
                        b.append(String.format("\\u%04x", (int) ch));
                    } else {
                        b.append(ch);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
