package com.example.capstone.background_jobs.model;

public class CreateTicketRequestPayload {

    private Long tenantId;
    private String findingId;
    private String summary;
    private String description;

    public CreateTicketRequestPayload(Long tenantId, String id, String summary, String desc) {
        this.tenantId = tenantId;
        this.findingId = id;
        this.summary = summary;
        this.description = desc;
    }

    public CreateTicketRequestPayload() {
    }

    // Getters & Setters
    public Long getTenantId() {
        return tenantId;
    }
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
    public String getFindingId() {
        return findingId;
    }
    public void setFindingId(String findingId) {
        this.findingId = findingId;
    }
    public String getSummary() {
        return summary;
    }
    public void setSummary(String summary) {
        this.summary = summary;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
}
