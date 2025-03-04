package com.example.capstone.background_jobs.model;

import java.util.List;

public class NewScanRunbookPayload {

    private Long tenantId;
    private String toolType;          // e.g. "CODE_SCANNING", "DEPENDABOT", etc.
    private List<String> newFindingIds;  // newly indexed ES document IDs

    public NewScanRunbookPayload() { }

    public NewScanRunbookPayload(Long tenantId, String toolType, List<String> newFindingIds) {
        this.tenantId = tenantId;
        this.toolType = toolType;
        this.newFindingIds = newFindingIds;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getToolType() {
        return toolType;
    }

    public void setToolType(String toolType) {
        this.toolType = toolType;
    }

    public List<String> getNewFindingIds() {
        return newFindingIds;
    }

    public void setNewFindingIds(List<String> newFindingIds) {
        this.newFindingIds = newFindingIds;
    }
}
