package com.newtonite.ops.repository;

import com.newtonite.ops.model.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access repository for IdempotencyRecord entities.
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Looks up an existing cached response record by the idempotency key.
     */
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
