package com.xzkj.hv2ops;

/** 能直接告诉操作的人的错误：只打印信息，不打印调用堆栈。 */
public class OpsException extends RuntimeException {

    public OpsException(String message) {
        super(message);
    }
}
