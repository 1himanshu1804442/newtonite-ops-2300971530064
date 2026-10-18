package com.newtonite.ops.controller;

import com.newtonite.ops.dto.*;
import com.newtonite.ops.model.enums.Priority;
import com.newtonite.ops.model.enums.Status;
import com.newtonite.ops.model.enums.WorkItemType;
import com.newtonite.ops.service.ActivityAuditService;
import com.newtonite.ops.service.WorkItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing endpoints for work item operational lifecycle.
 * Strictly delegates all business logic to WorkItemService and ActivityAuditService.
 */
@Slf4j
@RestController
@RequestMapping("/api/work-items")
@RequiredArgsConstructor
public class WorkItemController {

    private final WorkItemService workItemService;
    private final ActivityAuditService activityAuditService;

    /**
     * Lists work items with multi-criteria filtering and pagination.
     */
    @GetMapping
    public ResponseEntity<PageResponse<WorkItemResponse>> listWorkItems(
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) WorkItemType type,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        log.info("REST: GET /api/work-items with teamId={}, status={}, page={}, size={}", teamId, status, page, size);
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortBy));

        PageResponse<WorkItemResponse> response = workItemService.listWorkItems(
                teamId, status, priority, type, assigneeId, search, pageable
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves work item details by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkItemResponse> getWorkItem(@PathVariable UUID id) {
        log.info("REST: GET /api/work-items/{}", id);
        return ResponseEntity.ok(workItemService.getWorkItemById(id));
    }

    /**
     * Creates a new work item.
     */
    @PostMapping
    public ResponseEntity<WorkItemResponse> createWorkItem(@Valid @RequestBody CreateWorkItemRequest request) {
        log.info("REST: POST /api/work-items for title='{}'", request.getTitle());
        WorkItemResponse response = workItemService.createWorkItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates an existing work item with optimistic concurrency validation.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<WorkItemResponse> updateWorkItem(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkItemRequest request
    ) {
        log.info("REST: PATCH /api/work-items/{} with expectedVersion={}", id, request.getExpectedVersion());
        return ResponseEntity.ok(workItemService.updateWorkItem(id, request));
    }

    /**
     * Executes atomic concurrent claim.
     * Returns 409 Conflict if already claimed by another user.
     */
    @PostMapping("/{id}/claim")
    public ResponseEntity<WorkItemResponse> claimWorkItem(
            @PathVariable UUID id,
            @Valid @RequestBody ClaimWorkItemRequest request
    ) {
        log.info("REST: POST /api/work-items/{}/claim by userId={}", id, request.getUserId());
        return ResponseEntity.ok(workItemService.claimWorkItem(id, request.getUserId()));
    }

    /**
     * Explicitly assigns a work item to an operator.
     */
    @PostMapping("/{id}/assign")
    public ResponseEntity<WorkItemResponse> assignWorkItem(
            @PathVariable UUID id,
            @Valid @RequestBody AssignWorkItemRequest request
    ) {
        log.info("REST: POST /api/work-items/{}/assign to assigneeId={}", id, request.getAssigneeId());
        return ResponseEntity.ok(workItemService.assignWorkItem(id, request));
    }

    /**
     * Executes workflow state machine transition.
     * Enforces transition sequence and role requirements.
     */
    @PostMapping("/{id}/transition")
    public ResponseEntity<WorkItemResponse> transitionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionWorkItemRequest request
    ) {
        log.info("REST: POST /api/work-items/{}/transition to status={}", id, request.getTargetStatus());
        return ResponseEntity.ok(workItemService.transitionStatus(id, request));
    }

    /**
     * Retrieves audit trail activities for a work item.
     */
    @GetMapping("/{id}/activities")
    public ResponseEntity<List<ActivityEventResponse>> getActivities(@PathVariable UUID id) {
        log.info("REST: GET /api/work-items/{}/activities", id);
        return ResponseEntity.ok(activityAuditService.getActivitiesForWorkItem(id));
    }

    /**
     * Retrieves aggregated metrics and operational dashboard stats.
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        log.info("REST: GET /api/work-items/stats");
        return ResponseEntity.ok(workItemService.getDashboardStats());
    }
}
