package com.xzkj.hv2.positioning;

import java.time.LocalDateTime;

/** 写入 POS_INGEST_FILE 的一行；ID 由数据库生成后回填（所以不用 record）。 */
public class IngestFileEntry {

    private Long id;
    private final String fileName;
    private final String fileType;
    private final String mineCode;
    private final LocalDateTime headerTime;
    private final String sha256;
    private final Long sizeBytes;
    private final String status;
    private final Integer recordCount;
    private final Integer errorCount;
    private final String errorMsg;
    private final LocalDateTime receivedAt;
    private final String backupPath;

    public IngestFileEntry(String fileName, String fileType, String mineCode, LocalDateTime headerTime,
                           String sha256, Long sizeBytes, IngestStatus status, Integer recordCount,
                           Integer errorCount, String errorMsg, LocalDateTime receivedAt, String backupPath) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.mineCode = mineCode;
        this.headerTime = headerTime;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.status = status.name();
        this.recordCount = recordCount;
        this.errorCount = errorCount;
        this.errorMsg = errorMsg == null || errorMsg.length() <= 500 ? errorMsg : errorMsg.substring(0, 500);
        this.receivedAt = receivedAt;
        this.backupPath = backupPath;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public String getMineCode() {
        return mineCode;
    }

    public LocalDateTime getHeaderTime() {
        return headerTime;
    }

    public String getSha256() {
        return sha256;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getStatus() {
        return status;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getBackupPath() {
        return backupPath;
    }
}
