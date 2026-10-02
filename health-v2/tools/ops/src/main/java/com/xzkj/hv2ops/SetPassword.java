package com.xzkj.hv2ops;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 改登录密码（docs/11"改 admin 密码"）。新密码运行时输入两次、不回显，只把 BCrypt 哈希写进 SYS_USER；
 * 同一个事务里记一条操作日志 USER_PASSWORD_SET（不含密码和哈希）。
 * 后端的登录令牌存在内存里，改完重启后端，之前登录的人都要重新登录。
 */
final class SetPassword {

    static final int MIN_LENGTH = 10;

    private SetPassword() {
    }

    static int run(Db db, String username, Prompt prompt) {
        String first = prompt.secret("新密码（至少 " + MIN_LENGTH + " 位，要有字母和数字）：");
        String problem = check(username, first);
        if (problem != null) {
            throw new OpsException(problem);
        }
        if (!first.equals(prompt.secret("再输入一次："))) {
            throw new OpsException("两次输入不一致，没有修改");
        }
        String hash = new BCryptPasswordEncoder(10).encode(first);
        try (Connection c = db.open()) {
            try {
                if (!exists(c, username)) {
                    throw new OpsException("没有登录名为 " + username + " 的账号");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE SYS_USER SET PASSWORD_HASH = ? WHERE USERNAME = ?")) {
                    ps.setString(1, hash);
                    ps.setString(2, username);
                    ps.executeUpdate();
                }
                OpLog.write(c, "USER_PASSWORD_SET", "USER", username, null,
                        "{\"tool\":\"hv2-ops\",\"osUser\":" + OpLog.json(System.getProperty("user.name")) + "}");
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new OpsException("写库失败：ORA-" + e.getErrorCode() + "，没有修改");
        }
        System.out.println("账号 " + username + " 的密码已修改。重启后端后，之前登录的令牌全部失效。");
        return 0;
    }

    /** 密码规则；合格返回 null，不合格返回原因。 */
    static String check(String username, String password) {
        if (password.length() < MIN_LENGTH) {
            return "密码至少 " + MIN_LENGTH + " 位";
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            return "密码要同时有字母和数字";
        }
        if (password.toLowerCase().contains(username.toLowerCase()) || password.toLowerCase().contains("admin")) {
            return "密码不能包含登录名或 admin";
        }
        if (!password.strip().equals(password)) {
            return "密码首尾不能有空格";
        }
        return null;
    }

    private static boolean exists(Connection c, String username) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM SYS_USER WHERE USERNAME = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }
}
