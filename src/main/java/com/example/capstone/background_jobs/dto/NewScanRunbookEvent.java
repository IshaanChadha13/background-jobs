package com.example.capstone.background_jobs.dto;

import com.example.capstone.background_jobs.model.Event;
import com.example.capstone.background_jobs.model.EventTypes;
import com.example.capstone.background_jobs.model.NewScanRunbookPayload;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NewScanRunbookEvent implements Event<NewScanRunbookPayload> {

    private String eventId;
    private NewScanRunbookPayload payload;
    private String destinationTopic;  // where we want JFC to forward it

    public NewScanRunbookEvent() {
    }

    public NewScanRunbookEvent(NewScanRunbookPayload payload, String destinationTopic) {
        this.eventId = UUID.randomUUID().toString();
        this.payload = payload;
        this.destinationTopic = destinationTopic;
    }

    @Override
    public EventTypes getType() {
        return EventTypes.NEW_SCAN; // We add NEW_SCAN to our EventTypes enum
    }

    @Override
    public NewScanRunbookPayload getPayload() {
        return payload;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    public String getDestinationTopic() {
        return destinationTopic;
    }

    public void setDestinationTopic(String destinationTopic) {
        this.destinationTopic = destinationTopic;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setPayload(NewScanRunbookPayload payload) {
        this.payload = payload;
    }
}
