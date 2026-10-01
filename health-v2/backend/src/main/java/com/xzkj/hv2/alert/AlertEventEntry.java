package com.xzkj.hv2.alert;

import java.time.LocalDateTime;

/** 新建一条 ALERT_EVENT；插入后回填 ID。 */
public class AlertEventEntry {

    private Long id;
    private final String cardCode;
    private final String src;
    private final String code;
    private final String category;
    private final int severity;
    private final String valText;
    private final String ruleGroup;
    private final LocalDateTime occurredAt;
    private final String deviceImei;

    public AlertEventEntry(String cardCode, String src, String code, String category, int severity, String valText,
                           String ruleGroup, LocalDateTime occurredAt, String deviceImei) {
        this.cardCode = cardCode;
        this.src = src;
        this.code = code;
        this.category = category;
        this.severity = severity;
        this.valText = valText;
        this.ruleGroup = ruleGroup;
        this.occurredAt = occurredAt;
        this.deviceImei = deviceImei;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCardCode() {
        return cardCode;
    }

    public String getSrc() {
        return src;
    }

    public String getCode() {
        return code;
    }

    public String getCategory() {
        return category;
    }

    public int getSeverity() {
        return severity;
    }

    public String getValText() {
        return valText;
    }

    public String getRuleGroup() {
        return ruleGroup;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getDeviceImei() {
        return deviceImei;
    }
}
