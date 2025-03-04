package com.example.capstone.background_jobs.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "runbook_config")
public class RunbookConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ties to runbook.runbook_id (a string)
    @Column(name = "runbook_id", length = 36, nullable = false)
    private String runbookId;

    @Column(name = "trigger_type", length = 50)
    private String trigger;

    @Lob
    @Column(name = "filters_json")
    private String filtersJson; // e.g. '{"state":"DISMISSED","severity":"HIGH"}'

    @Lob
    @Column(name = "actions_json")
    private String actionsJson; // e.g. '{"update_finding": {...}, "create_ticket": true}'

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Constructors
    public RunbookConfigEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRunbookId() {
        return runbookId;
    }

    public void setRunbookId(String runbookId) {
        this.runbookId = runbookId;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getFiltersJson() {
        return filtersJson;
    }

    public void setFiltersJson(String filtersJson) {
        this.filtersJson = filtersJson;
    }

    public String getActionsJson() {
        return actionsJson;
    }

    public void setActionsJson(String actionsJson) {
        this.actionsJson = actionsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

