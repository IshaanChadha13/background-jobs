package com.example.capstone.background_jobs.producer;

import com.example.capstone.background_jobs.dto.UpdateAcknowledgement;
import com.example.capstone.background_jobs.model.AcknowledgementEvent;
import com.example.capstone.background_jobs.model.AcknowledgementStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class AcknowledgementProducer {

    @Value("${kafka.topics.job-acknowledgement-topic}")
    private String jobAckTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AcknowledgementProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public void sendUpdateAck(String jobId, boolean success) {
        try {
            AcknowledgementEvent ackEvent = new AcknowledgementEvent(jobId);
            ackEvent.setStatus(success ? AcknowledgementStatus.SUCCESS : AcknowledgementStatus.FAILURE);

            UpdateAcknowledgement ack = new UpdateAcknowledgement(null, ackEvent);

            String json = objectMapper.writeValueAsString(ack);
            kafkaTemplate.send(jobAckTopic, json);
            System.out.println("[background-jobs] Sent UpdateAcknowledgement => " + json);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

