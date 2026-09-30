package com.xzkj.hv2.positioning;

/**
 * 文件里有问题的一条记录（写入 POS_INGEST_ERROR）。
 *
 * @param kind    ERROR：记录被跳过；WARN：记录已入库，但需要人工核对
 * @param lineNo  第几条记录，文件头之后从 1 数起
 * @param rawText 记录原文（含姓名，只存库供运维排查，不写日志）
 */
public record RecordIssue(Kind kind, int lineNo, String rawText, String reason) {

    public enum Kind { ERROR, WARN }

    static RecordIssue error(int lineNo, String rawText, String reason) {
        return new RecordIssue(Kind.ERROR, lineNo, rawText, reason);
    }

    static RecordIssue warn(int lineNo, String rawText, String reason) {
        return new RecordIssue(Kind.WARN, lineNo, rawText, reason);
    }
}
