package com.newtonite.ops.repository;

import com.newtonite.ops.model.entity.OutboxJob;
import com.newtonite.ops.model.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access repository for Transactional Outbox jobs.
 */
@Repository
public interface OutboxJobRepository extends JpaRepository<OutboxJob, UUID> {

    /**
     * Polls pending jobs eligible for background processing that have not exceeded max retry attempts.
     */
    List<OutboxJob> findByStatusAndAttemptsLessThanOrderByCreatedAtAsc(
        OutboxStatus status,
        int maxAttempts,
        Pageable pageable
    );

    /**
     * Retrieves the most recently created outbox jobs for dashboard monitoring.
     */
    List<OutboxJob> findTop50ByOrderByCreatedAtDesc();
}
