package com.newtonite.ops.repository;

import com.newtonite.ops.model.entity.User;
import com.newtonite.ops.model.entity.WorkItem;
import com.newtonite.ops.model.enums.Priority;
import com.newtonite.ops.model.enums.Status;
import com.newtonite.ops.model.enums.WorkItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data access repository for WorkItem entities with support for atomic concurrent claims,
 * optimistic concurrency versioning, and flexible multi-criteria filtering.
 */
@Repository
public interface WorkItemRepository extends JpaRepository<WorkItem, UUID>, JpaSpecificationExecutor<WorkItem> {

    /**
     * Executes an atomic database-level update to assign an unassigned work item.
     * Uses conditional WHERE clause (w.assignee IS NULL) to prevent race conditions.
     * Increments version to satisfy optimistic locking semantics.
     *
     * @param id The work item ID to claim
     * @param user The user claiming the work item
     * @param now Current timestamp for updatedAt
     * @return Number of rows updated (1 if successfully claimed, 0 if already claimed or not found)
     */
    // flushAutomatically = true flushes pending changes before the query runs.
    // clearAutomatically = true clears Hibernate L1 persistence cache so subsequent findById reloads fresh entity.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WorkItem w SET w.assignee = :user, w.version = w.version + 1, w.updatedAt = :now WHERE w.id = :id AND w.assignee IS NULL")
    int claimWorkItemAtomic(@Param("id") UUID id, @Param("user") User user, @Param("now") LocalDateTime now);

    /**
     * Counts total work items matching a given status.
     */
    long countByStatus(Status status);

    /**
     * Counts total work items matching a given priority.
     */
    long countByPriority(Priority priority);

    /**
     * Counts total work items matching a given work item type.
     */
    long countByType(WorkItemType type);

    /**
     * Finds work items assigned to a specific user.
     */
    Page<WorkItem> findByAssigneeId(UUID assigneeId, Pageable pageable);

    /**
     * Finds work items associated with a specific team.
     */
    Page<WorkItem> findByTeamId(UUID teamId, Pageable pageable);
}
