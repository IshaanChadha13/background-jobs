package com.example.capstone.background_jobs.consumer;

import com.example.capstone.background_jobs.dto.CreateTicketRequestEvent;
import com.example.capstone.background_jobs.dto.TransitionTicketRequestEvent;
import com.example.capstone.background_jobs.dto.UpdateAlertEvent;
import com.example.capstone.background_jobs.model.EventTypes;
import com.example.capstone.background_jobs.model.TransitionTicketRequestPayload;
import com.example.capstone.background_jobs.service.BackgroundJobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class BackgroundJobsConsumer {

    private final ObjectMapper objectMapper;
    private final BackgroundJobService jobService;

    public BackgroundJobsConsumer(BackgroundJobService jobService) {
        this.jobService = jobService;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = "${kafka.topics.jfc-bg-job-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onJfcJobMessage(String message) {
        try {
            // 1) Peek at "type" to see which event type we have
            EventTypes eventType = detectEventType(message);
            switch (eventType) {
                case UPDATE_FINDING -> handleUpdateFinding(message);
                case CREATE_TICKET -> handleCreateTicket(message);
                case TRANSITION_TICKET -> handleTransitionTicket(message);
                default -> {
                    System.out.println("[JFC] Unknown event type => " + eventType);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private EventTypes detectEventType(String rawJson) throws Exception {
        String typeStr = objectMapper.readTree(rawJson).path("type").asText();
        return EventTypes.valueOf(typeStr.toUpperCase());
    }

    private void handleUpdateFinding(String message) throws Exception {
        try {
            UpdateAlertEvent event = objectMapper.readValue(message, UpdateAlertEvent.class);
            jobService.handleUpdateFinding(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleCreateTicket(String message) throws Exception {
        try {
            CreateTicketRequestEvent event = objectMapper.readValue(message, CreateTicketRequestEvent.class);
            jobService.handleCreateTicket(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleTransitionTicket(String message) throws Exception {
        try {
            TransitionTicketRequestEvent event = objectMapper.readValue(message, TransitionTicketRequestEvent.class);
            jobService.handleTransitionTicket(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

