package com.newtonite.ops.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health check endpoint for container orchestrators and readiness probes.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("REST: GET /api/health");
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "newtonite-ops-backend",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
