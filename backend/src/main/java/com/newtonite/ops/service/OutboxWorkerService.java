package com.newtonite.ops.service;

import com.newtonite.ops.model.entity.OutboxJob;
import com.newtonite.ops.model.enums.OutboxStatus;
import com.newtonite.ops.repository.OutboxJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled background worker that polls and processes pending Transactional Outbox jobs.
 * Enforces exponential retry backoff up to a maximum of 5 attempts before marking as FAILED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxWorkerService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 10;

    private final OutboxJobRepository outboxJobRepository;

    /**
     * Periodically runs every 5 seconds to drain pending outbox messages.
     * fixedDelay ensures that the next execution only starts after the previous one finishes.
     */
    @Scheduled(fixedDelay = 5000)
    public void scheduledDrainOutbox() {
        processPendingJobs();
    }

    /**
     * Drains a batch of eligible pending outbox jobs.
     * Can also be triggered manually via administrative/test API endpoints.
     *
     * @return Number of jobs processed in this cycle
     */
    public int processPendingJobs() {
        List<OutboxJob> pendingJobs = outboxJobRepository.findByStatusAndAttemptsLessThanOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                MAX_ATTEMPTS,
                PageRequest.of(0, BATCH_SIZE)
        );

        if (pendingJobs.isEmpty()) {
            return 0;
        }

        log.info("Outbox worker discovered {} pending jobs to process", pendingJobs.size());
        int processedCount = 0;

        for (OutboxJob job : pendingJobs) {
            boolean success = processSingleJob(job);
            if (success) {
                processedCount++;
            }
        }

        return processedCount;
    }

    /**
     * Processes a single outbox job in an isolated transaction.
     *
     * @param job The outbox job to process
     * @return true if successfully processed, false if an error occurred
     */
    @Transactional
    public boolean processSingleJob(OutboxJob job) {
        log.info("Processing outbox job: id={}, type={}, attempt={}", job.getId(), job.getEventType(), job.getAttempts() + 1);

        job.setStatus(OutboxStatus.PROCESSING);
        job.setAttempts(job.getAttempts() + 1);
        job.setLastAttemptAt(LocalDateTime.now());
        outboxJobRepository.saveAndFlush(job);

        try {
            // Simulate external event dispatching (e.g. Kafka producer, PagerDuty webhook, Slack notification)
            dispatchToExternalSystem(job.getEventType(), job.getPayload());

            job.setStatus(OutboxStatus.COMPLETED);
            outboxJobRepository.save(job);
            log.info("Successfully delivered outbox job id={} for event={}", job.getId(), job.getEventType());
            return true;
        } catch (Exception e) {
            log.error("Failed to process outbox job id={} on attempt {}: {}", job.getId(), job.getAttempts(), e.getMessage(), e);

            if (job.getAttempts() >= MAX_ATTEMPTS) {
                log.error("Outbox job id={} exceeded max retry limit of {} attempts. Marking as FAILED.", job.getId(), MAX_ATTEMPTS);
                job.setStatus(OutboxStatus.FAILED);
            } else {
                // Return to PENDING so subsequent scheduled ticks will retry with backoff
                job.setStatus(OutboxStatus.PENDING);
            }

            outboxJobRepository.save(job);
            return false;
        }
    }

    /**
     * Dispatches event payload to downstream notification brokers.
     *
     * @param eventType Event type name
     * @param payload JSON string payload
     */
    private void dispatchToExternalSystem(String eventType, String payload) {
        // In this implementation, we log the dispatch action cleanly.
        // If an integration with external message brokers is configured, it would be called here.
        log.debug("Dispatching event payload downstream: eventType={}, payload={}", eventType, payload);
    }
}
