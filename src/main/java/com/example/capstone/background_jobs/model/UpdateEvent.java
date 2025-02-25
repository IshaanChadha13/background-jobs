package com.example.capstone.background_jobs.model;

public class UpdateEvent {

    private String tenantId;
    private String toolType;
    private long alertNumber;
    private String newState;
    private String reason;
    public UpdateEvent() {}
    public UpdateEvent(String tenantId, String toolType,
                       long alertNumber, String newState, String reason) {
        this.tenantId = tenantId;
        this.toolType = toolType;
        this.alertNumber = alertNumber;
        this.newState = newState;
        this.reason = reason;
    }
    // Getters and setters
    public String getToolType() {
        return toolType;
    }
    public void setToolType(String toolType) {
        this.toolType = toolType;
    }
    public long getAlertNumber() {
        return alertNumber;
    }
    public void setAlertNumber(long alertNumber) {
        this.alertNumber = alertNumber;
    }
    public String getNewState() {
        return newState;
    }
    public void setNewState(String newState) {
        this.newState = newState;
    }
    public String getReason() {
        return reason;
    }
    public void setReason(String reason) {
        this.reason = reason;
    }
    public String getTenantId() {
        return tenantId;
    }
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    @Override
    public String toString() {
        return "UpdateEvent {tenantId=" + tenantId + ", toolType=" + toolType + ", alertNumber=" + alertNumber
                + ", newState=" + newState + ", reason=" + reason + "}";
    }
}
