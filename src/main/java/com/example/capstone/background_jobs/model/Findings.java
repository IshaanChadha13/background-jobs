package com.example.capstone.background_jobs.model;

import java.util.Map;

public class Findings {

    private String id;            // internal unique ID (UUID)
    private String title;         // short title
    private String description;   // description text
    private String toolType;      // CODE_SCANNING, DEPENDABOT, SECRET_SCANNING
    private Severity severity;      // CRITICAL, HIGH, MEDIUM, LOW, etc.
    private AlertState state;         // open, false positive, suppressed, fixed, etc.
    private String createdAt;     // timestamp
    private String updatedAt;     // timestamp
    private String url;           // link
    private String cve;           // optional
    private String cwe;           // optional
    private String cvss;          // optional
    private String location;
    private String ticketId;

    public String getAlertNumber() {
        return alertNumber;
    }

    public void setAlertNumber(String alertNumber) {
        this.alertNumber = alertNumber;
    }

    private String alertNumber;// file path, etc.

    private Map<String, Object> additionalData;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getToolType() {
        return toolType;
    }

    public void setToolType(String toolType) {
        this.toolType = toolType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public AlertState getState() {
        return state;
    }

    public void setState(AlertState state) {
        this.state = state;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCve() {
        return cve;
    }

    public void setCve(String cve) {
        this.cve = cve;
    }

    public String getCwe() {
        return cwe;
    }

    public void setCwe(String cwe) {
        this.cwe = cwe;
    }

    public String getCvss() {
        return cvss;
    }

    public void setCvss(String cvss) {
        this.cvss = cvss;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(Map<String, Object> additionalData) {
        this.additionalData = additionalData;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }
}
