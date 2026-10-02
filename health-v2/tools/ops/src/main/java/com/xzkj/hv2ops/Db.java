package com.xzkj.hv2ops;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.regex.Pattern;

/** 数据库连接信息，只从环境变量读（和后端同名，docs/01 配置表）；密码没设时运行中输入。 */
public record Db(String url, String user, String password, String schema) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_$#]{0,127}");

    static Db fromEnv() {
        return fromEnv(System.getenv(), Prompt.system());
    }

    static Db fromEnv(Map<String, String> env, Prompt prompt) {
        String user = env.get("ORACLE_USER");
        if (blank(user)) {
            throw new OpsException("请先设置环境变量 ORACLE_USER（以及 ORACLE_HOST、ORACLE_PORT、ORACLE_SERVICE）");
        }
        String url = "jdbc:oracle:thin:@//" + orDefault(env.get("ORACLE_HOST"), "127.0.0.1") + ":"
                + orDefault(env.get("ORACLE_PORT"), "1521") + "/" + orDefault(env.get("ORACLE_SERVICE"), "FREEPDB1");
        String schema = env.get("ORACLE_SCHEMA");
        if (!blank(schema) && !NAME.matcher(schema.trim()).matches()) {
            throw new OpsException("ORACLE_SCHEMA 不是合法的 Oracle 账号名");
        }
        String password = env.get("ORACLE_PASSWORD");
        if (blank(password)) {
            password = prompt.secret("数据库账号 " + user + " 的密码：");
        }
        return new Db(url, user.trim(), password, blank(schema) ? null : schema.trim().toUpperCase());
    }

    /** 打开连接：会话时区北京时间；配了 ORACLE_SCHEMA 时切过去（和后端 OracleSessionConfig 一样）。不自动提交。 */
    Connection open() {
        try {
            Connection c = DriverManager.getConnection(url, user, password);
            try (Statement st = c.createStatement()) {
                st.execute("ALTER SESSION SET TIME_ZONE = 'Asia/Shanghai'");
                if (schema != null) {
                    st.execute("ALTER SESSION SET CURRENT_SCHEMA = " + schema);
                }
            }
            c.setAutoCommit(false);
            return c;
        } catch (SQLException e) {
            // 只报 Oracle 错误码，不报原文（原文里可能带着连接信息）
            throw new OpsException("连不上数据库 " + url + "（用户 " + user + "）：ORA-" + e.getErrorCode());
        }
    }

    @Override
    public String toString() {
        return "Db[" + url + ", " + user + (schema == null ? "" : ", schema " + schema) + "]";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String orDefault(String s, String d) {
        return blank(s) ? d : s.trim();
    }
}
