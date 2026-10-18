package com.newtonite.ops.controller;

import com.newtonite.ops.dto.OutboxJobResponse;
import com.newtonite.ops.service.OutboxService;
import com.newtonite.ops.service.OutboxWorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for monitoring and manually triggering Transactional Outbox job processing.
 */
@Slf4j
@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
public class OutboxController {

    private final OutboxService outboxService;
    private final OutboxWorkerService outboxWorkerService;

    /**
     * Lists the most recent 50 outbox jobs and their current processing status.
     */
    @GetMapping
    public ResponseEntity<List<OutboxJobResponse>> getRecentJobs() {
        log.info("REST: GET /api/outbox");
        return ResponseEntity.ok(outboxService.getRecentJobs());
    }

    /**
     * Manually triggers an outbox draining run.
     * Useful for integration testing, demonstrations, or forcing an immediate retry.
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> triggerOutboxProcessing() {
        log.info("REST: POST /api/outbox/process");
        int count = outboxWorkerService.processPendingJobs();
        return ResponseEntity.ok(Map.of(
                "message", "Outbox processing completed",
                "processedCount", count
        ));
    }
}
