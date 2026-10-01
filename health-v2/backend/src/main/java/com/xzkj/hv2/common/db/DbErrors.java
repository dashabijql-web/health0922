package com.xzkj.hv2.common.db;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.NestedExceptionUtils;

/**
 * 数据库错误写日志时只取错误码。Oracle 的错误信息里可能带着写入的值（如 ORA-01438 会带上超长的数字），
 * 而日志里不能出现个人健康数据（docs/01"安全"）。
 */
public final class DbErrors {

    private static final Pattern ORA_CODE = Pattern.compile("ORA-\\d{5}");

    private DbErrors() {
    }

    /** ORA-xxxxx；取不到时给异常类型名。 */
    public static String code(Throwable e) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        Matcher m = ORA_CODE.matcher(String.valueOf(root.getMessage()));
        return m.find() ? m.group() : root.getClass().getSimpleName();
    }
}
