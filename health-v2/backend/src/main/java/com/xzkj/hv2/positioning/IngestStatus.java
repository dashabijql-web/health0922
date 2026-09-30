package com.xzkj.hv2.positioning;

/** POS_INGEST_FILE.STATUS（docs/04）。 */
public enum IngestStatus {
    /** 全部记录入库 */
    DONE,
    /** 部分记录出错被跳过，其余已入库 */
    PARTIAL,
    /** 整个文件失败，已移到失败目录 */
    FAILED,
    /** 文件头时间早于同类型已生效的最新文件：数据照规则写入，但它不会成为"最新快照" */
    STALE,
    /** 同名同摘要的文件已处理过，不再入库 */
    DUPLICATE,
    /** 不解析的类型（如 RYXZ、RYQJ），只备份 */
    UNKNOWN
}
