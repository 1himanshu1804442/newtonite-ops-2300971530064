package com.newtonite.ops.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newtonite.ops.dto.OutboxJobResponse;
import com.newtonite.ops.model.entity.OutboxJob;
import com.newtonite.ops.model.enums.OutboxStatus;
import com.newtonite.ops.repository.OutboxJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing outbox jobs in accordance with the Transactional Outbox pattern.
 * Saves outbox events in the same transaction as entity changes to guarantee at-least-once delivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxJobRepository outboxJobRepository;
    private final ObjectMapper objectMapper;

    /**
     * Enqueues an outbox job atomically within the current transaction.
     *
     * @param eventType Identifier describing the event (e.g. WORK_ITEM_CLAIMED, STATUS_TRANSITIONED)
     * @param payload Payload object to serialize as JSON
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxJob scheduleJob(String eventType, Object payload) {
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for type: {}", eventType, e);
            jsonPayload = "{\"error\": \"Serialization failure\", \"raw\": \"" + payload.toString() + "\"}";
        }

        OutboxJob job = OutboxJob.builder()
                .eventType(eventType)
                .payload(jsonPayload)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        OutboxJob savedJob = outboxJobRepository.save(job);
        log.info("Scheduled transactional outbox job: id={}, eventType={}", savedJob.getId(), eventType);
        return savedJob;
    }

    /**
     * Retrieves recent outbox jobs for operational monitoring.
     */
    @Transactional(readOnly = true)
    public List<OutboxJobResponse> getRecentJobs() {
        return outboxJobRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(OutboxJobResponse::fromEntity)
                .toList();
    }
}
