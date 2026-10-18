package com.newtonite.ops.service;

import com.newtonite.ops.model.entity.IdempotencyRecord;
import com.newtonite.ops.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing idempotency records for network retry deduplication.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    /**
     * Looks up an existing idempotency record by key.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findRecord(String idempotencyKey) {
        return idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);
    }

    /**
     * Persists the HTTP response status code and body for a processed idempotency key.
     * Uses REQUIRES_NEW so the idempotency record is recorded even if adjacent outer operations rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRecord(String idempotencyKey, UUID userId, String requestHash, int statusCode, String responseBody) {
        log.info("Saving idempotency record: key={}, statusCode={}", idempotencyKey, statusCode);

        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .userId(userId)
                .requestHash(requestHash)
                .statusCode(statusCode)
                .responseBody(responseBody)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            idempotencyRecordRepository.saveAndFlush(record);
        } catch (Exception e) {
            log.warn("Could not save idempotency record for key {} (possibly duplicate insertion): {}", idempotencyKey, e.getMessage());
        }
    }
}
