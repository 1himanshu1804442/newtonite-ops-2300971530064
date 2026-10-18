package com.newtonite.ops.repository;

import com.newtonite.ops.model.entity.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access repository for ActivityEvent audit records.
 */
@Repository
public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    /**
     * Retrieves the chronological audit history for a specific work item (newest first).
     */
    List<ActivityEvent> findByWorkItemIdOrderByCreatedAtDesc(UUID workItemId);

    /**
     * Retrieves the most recent system-wide operational activity events.
     */
    List<ActivityEvent> findTop20ByOrderByCreatedAtDesc();
}
