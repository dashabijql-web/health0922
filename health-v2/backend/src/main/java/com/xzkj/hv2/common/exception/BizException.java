package com.xzkj.hv2.common.exception;

import org.springframework.http.HttpStatus;

/** 业务异常：带 HTTP 状态码和给用户看的提示语，由 {@link GlobalExceptionHandler} 转成统一返回格式。 */
public class BizException extends RuntimeException {

    private final HttpStatus status;

    public BizException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BizException badRequest(String message) {
        return new BizException(HttpStatus.BAD_REQUEST, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(HttpStatus.UNAUTHORIZED, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(HttpStatus.FORBIDDEN, message);
    }

    public static BizException conflict(String message) {
        return new BizException(HttpStatus.CONFLICT, message);
    }
}
