package com.xzkj.hv2.common.api;

/**
 * 统一返回格式 { code, message, data }（docs/01 第五节）。
 * code = 0 表示成功；失败时 code 与 HTTP 状态码相同（401、403、400、409、429、500 等）。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static final int OK = 0;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(OK, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(OK, "ok", null);
    }

    public static ApiResponse<Void> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
