package com.example.capstone.background_jobs.dto;

import com.example.capstone.background_jobs.model.Event;
import com.example.capstone.background_jobs.model.EventTypes;
import com.example.capstone.background_jobs.model.UpdateEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateAlertEvent implements Event<UpdateEvent> {

    private String eventId;
    private UpdateEvent payload;


    public UpdateAlertEvent() {}
    public UpdateAlertEvent(String eventId, UpdateEvent payload) {
        this.eventId = (eventId == null || eventId.isEmpty())
                ? UUID.randomUUID().toString() : eventId;
        this.payload = payload;
    }
    @Override
    public String getEventId() {
        return eventId;
    }
    @Override
    public EventTypes getType() {
        return EventTypes.UPDATE_FINDING;
    }
    @Override
    public UpdateEvent getPayload() {
        return payload;
    }
    public void setPayload(UpdateEvent payload) {
        this.payload = payload;
    }
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
}
