package com.example.capstone.background_jobs.model;

public class AcknowledgementEvent {

    private AcknowledgementStatus status = AcknowledgementStatus.SUCCESS;
    private String jobId;

    public AcknowledgementEvent() {}
    public AcknowledgementEvent(String jobId) {
        this.jobId = jobId;
    }
    public void setStatus(AcknowledgementStatus status) {
        this.status = status;
    }
    public AcknowledgementStatus getStatus() {
        return status;
    }
    public String getJobId() {
        return jobId;
    }
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}
