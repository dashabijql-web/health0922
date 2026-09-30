package com.xzkj.hv2.positioning;

/** 整个文件不能用（文件头不合法、编码错误、出错记录太多等），文件移到失败目录。 */
public class PositioningFormatException extends Exception {

    public PositioningFormatException(String message) {
        super(message);
    }
}
