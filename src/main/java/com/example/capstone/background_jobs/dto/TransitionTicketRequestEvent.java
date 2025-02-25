package com.example.capstone.background_jobs.dto;

import com.example.capstone.background_jobs.model.Event;
import com.example.capstone.background_jobs.model.EventTypes;
import com.example.capstone.background_jobs.model.TransitionTicketRequestPayload;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransitionTicketRequestEvent implements Event<TransitionTicketRequestPayload> {

    private TransitionTicketRequestPayload payload;
    private String eventId;
    private String destinationTopic;

    public TransitionTicketRequestEvent() {

    }

    public TransitionTicketRequestEvent(TransitionTicketRequestPayload payload, String eventId, String destinationTopic) {
        this.payload = payload;
        this.eventId = eventId;
        this.destinationTopic = destinationTopic;
    }

    @Override
    public EventTypes getType() {
        return EventTypes.TRANSITION_TICKET;
    }

    @Override
    public TransitionTicketRequestPayload getPayload() {
        return payload;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    public String getDestinationTopic() {
        return destinationTopic;
    }
}
