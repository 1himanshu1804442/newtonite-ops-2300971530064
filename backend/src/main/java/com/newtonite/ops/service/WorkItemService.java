package com.newtonite.ops.service;

import com.newtonite.ops.dto.*;
import com.newtonite.ops.exception.ConflictException;
import com.newtonite.ops.exception.ForbiddenException;
import com.newtonite.ops.exception.InvalidWorkflowTransitionException;
import com.newtonite.ops.exception.ResourceNotFoundException;
import com.newtonite.ops.model.entity.Team;
import com.newtonite.ops.model.entity.User;
import com.newtonite.ops.model.entity.WorkItem;
import com.newtonite.ops.model.enums.*;
import com.newtonite.ops.repository.WorkItemRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Core business service managing operational WorkItems.
 * Orchestrates atomic claims, optimistic locking validation, workflow state machine transitions,
 * audit trail logging, and outbox event dispatch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemService {

    private final WorkItemRepository workItemRepository;
    private final UserService userService;
    private final TeamService teamService;
    private final ActivityAuditService activityAuditService;
    private final OutboxService outboxService;

    /**
     * Lists work items matching multi-criteria filters with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<WorkItemResponse> listWorkItems(
            UUID teamId,
            Status status,
            Priority priority,
            WorkItemType type,
            UUID assigneeId,
            String search,
            Pageable pageable
    ) {
        log.info("Listing work items with filters: teamId={}, status={}, priority={}, type={}, assigneeId={}, search='{}'",
                teamId, status, priority, type, assigneeId, search);

        Specification<WorkItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (teamId != null) {
                predicates.add(cb.equal(root.get("team").get("id"), teamId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), assigneeId));
            }
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate titleLike = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descLike = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(titleLike, descLike));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WorkItem> page = workItemRepository.findAll(spec, pageable);
        return PageResponse.fromPage(page, WorkItemResponse::fromEntity);
    }

    /**
     * Retrieves work item details by ID, checking team-level resource authorization if actor is specified.
     */
    @Transactional(readOnly = true)
    public WorkItemResponse getWorkItemById(UUID id, UUID actorId) {
        log.info("Fetching work item details for id: {} by actorId: {}", id, actorId);
        WorkItem item = getWorkItemEntity(id);

        if (actorId != null) {
            User actor = userService.getUserEntity(actorId);
            if (!teamService.isUserMemberOfTeam(actor.getId(), item.getTeam().getId())) {
                log.error("Resource Authorization Violation: User {} does not belong to team {} for work item {}",
                        actor.getEmail(), item.getTeam().getCode(), id);
                throw new ForbiddenException("Access denied: User " + actor.getEmail() +
                        " is not a member of team '" + item.getTeam().getName() + "'.");
            }
        }

        return WorkItemResponse.fromEntity(item);
    }

    /**
     * Retrieves work item details by ID without explicit actor check.
     */
    @Transactional(readOnly = true)
    public WorkItemResponse getWorkItemById(UUID id) {
        return getWorkItemById(id, null);
    }

    /**
     * Internal lookup returning managed WorkItem JPA entity.
     */
    @Transactional(readOnly = true)
    public WorkItem getWorkItemEntity(UUID id) {
        return workItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work item not found with id: " + id));
    }

    /**
     * Creates a new work item, records creation audit event, and schedules transactional outbox job.
     */
    @Transactional
    public WorkItemResponse createWorkItem(CreateWorkItemRequest request) {
        log.info("Creating new work item: title='{}', type={}, priority={}, teamId={}",
                request.getTitle(), request.getType(), request.getPriority(), request.getTeamId());

        Team team = teamService.getTeamEntity(request.getTeamId());
        User creator = userService.getUserEntity(request.getCreatorId());
        User assignee = null;
        if (request.getAssigneeId() != null) {
            assignee = userService.getUserEntity(request.getAssigneeId());
        }

        WorkItem workItem = WorkItem.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .type(request.getType())
                .priority(request.getPriority())
                .status(Status.OPEN)
                .team(team)
                .creator(creator)
                .assignee(assignee)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        WorkItem saved = workItemRepository.save(workItem);

        // Record audit activity
        activityAuditService.logActivity(
                saved,
                creator,
                ActionType.CREATED,
                "Work item created with initial status OPEN",
                null,
                Status.OPEN.name()
        );

        // Schedule transactional outbox event
        outboxService.scheduleJob("WORK_ITEM_CREATED", Map.of(
                "workItemId", saved.getId(),
                "title", saved.getTitle(),
                "teamId", team.getId(),
                "creatorEmail", creator.getEmail()
        ));

        return WorkItemResponse.fromEntity(saved);
    }

    /**
     * Updates work item fields with optimistic concurrency version checking.
     * Prevents blind overwrite if concurrent modifications occurred.
     */
    @Transactional
    public WorkItemResponse updateWorkItem(UUID id, UpdateWorkItemRequest request) {
        log.info("Updating work item id={}, expectedVersion={}", id, request.getExpectedVersion());

        WorkItem item = getWorkItemEntity(id);
        User actor = userService.getUserEntity(request.getActorId());

        // Team isolation check: user outside team cannot edit items
        if (!teamService.isUserMemberOfTeam(actor.getId(), item.getTeam().getId())) {
            log.error("Resource Authorization: User {} is not a member of team {} and cannot update work item {}",
                    actor.getEmail(), item.getTeam().getCode(), id);
            throw new ForbiddenException("Access denied: User " + actor.getEmail() +
                    " is not a member of team '" + item.getTeam().getName() + "'.");
        }

        // Optimistic concurrency check: ensure client's view matches database version
        if (request.getExpectedVersion() != null && !item.getVersion().equals(request.getExpectedVersion())) {
            throw new ConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "Version mismatch detected for work item " + id + ". Expected version " +
                            request.getExpectedVersion() + ", but current database version is " + item.getVersion() +
                            ". Please refresh and reapply your changes.",
                    WorkItemResponse.fromEntity(item)
            );
        }

        String oldDetails = String.format("title='%s', priority=%s, type=%s", item.getTitle(), item.getPriority(), item.getType());

        item.setTitle(request.getTitle());
        item.setDescription(request.getDescription());
        item.setType(request.getType());
        item.setPriority(request.getPriority());
        item.setUpdatedAt(LocalDateTime.now());

        // Save entity - JPA @Version will also execute verification on commit
        WorkItem updated = workItemRepository.saveAndFlush(item);

        String newDetails = String.format("title='%s', priority=%s, type=%s", updated.getTitle(), updated.getPriority(), updated.getType());

        // Record audit activity
        activityAuditService.logActivity(
                updated,
                actor,
                ActionType.UPDATED,
                "Updated work item details: " + newDetails,
                oldDetails,
                newDetails
        );

        // Schedule transactional outbox event
        outboxService.scheduleJob("WORK_ITEM_UPDATED", Map.of(
                "workItemId", updated.getId(),
                "version", updated.getVersion(),
                "actorEmail", actor.getEmail()
        ));

        return WorkItemResponse.fromEntity(updated);
    }

    /**
     * Executes atomic concurrent claim.
     * Uses conditional database UPDATE query: UPDATE ... WHERE id = :id AND assignee IS NULL.
     * Returns 409 Conflict if 0 rows were updated.
     */
    @Transactional
    public WorkItemResponse claimWorkItem(UUID id, UUID userId) {
        log.info("Attempting atomic concurrent claim on work item id={} by user id={}", id, userId);

        User claimingUser = userService.getUserEntity(userId);
        WorkItem existingItem = getWorkItemEntity(id);

        if (!teamService.isUserMemberOfTeam(claimingUser.getId(), existingItem.getTeam().getId())) {
            log.error("Resource Authorization Violation: User {} is not a member of team {} for claim on item {}",
                    claimingUser.getEmail(), existingItem.getTeam().getCode(), id);
            throw new ForbiddenException("Access denied: User " + claimingUser.getEmail() +
                    " is not a member of team '" + existingItem.getTeam().getName() + "'.");
        }

        // Execute atomic conditional database update
        int rowsUpdated = workItemRepository.claimWorkItemAtomic(id, claimingUser, LocalDateTime.now());

        if (rowsUpdated == 0) {
            // Check whether work item exists or was already claimed
            WorkItem existing = workItemRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Work item not found with id: " + id));

            String currentAssignee = (existing.getAssignee() != null) ? existing.getAssignee().getName() : "another user";
            throw new ConflictException(
                    "CONCURRENT_CLAIM_CONFLICT",
                    "Work item '" + existing.getTitle() + "' (ID: " + id + ") has already been claimed by " + currentAssignee + "."
            );
        }

        // Fetch refreshed work item entity
        WorkItem claimed = getWorkItemEntity(id);

        // Record audit activity
        activityAuditService.logActivity(
                claimed,
                claimingUser,
                ActionType.CLAIMED,
                "Claimed work item atomically",
                "UNASSIGNED",
                claimingUser.getName()
        );

        // Schedule transactional outbox job
        outboxService.scheduleJob("WORK_ITEM_CLAIMED", Map.of(
                "workItemId", claimed.getId(),
                "claimedBy", claimingUser.getEmail(),
                "timestamp", LocalDateTime.now().toString()
        ));

        return WorkItemResponse.fromEntity(claimed);
    }

    /**
     * Explicitly assigns a work item to an operator.
     */
    @Transactional
    public WorkItemResponse assignWorkItem(UUID id, AssignWorkItemRequest request) {
        log.info("Assigning work item id={} to assigneeId={} by actorId={}", id, request.getAssigneeId(), request.getActorId());

        WorkItem item = getWorkItemEntity(id);
        User actor = userService.getUserEntity(request.getActorId());
        User newAssignee = userService.getUserEntity(request.getAssigneeId());

        if (request.getExpectedVersion() != null && !item.getVersion().equals(request.getExpectedVersion())) {
            throw new ConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "Version mismatch detected for work item " + id + ". Please refresh before assigning."
            );
        }

        String prevAssigneeName = (item.getAssignee() != null) ? item.getAssignee().getName() : "UNASSIGNED";
        item.setAssignee(newAssignee);
        item.setUpdatedAt(LocalDateTime.now());

        WorkItem saved = workItemRepository.saveAndFlush(item);

        activityAuditService.logActivity(
                saved,
                actor,
                ActionType.ASSIGNED,
                "Assigned work item to " + newAssignee.getName(),
                prevAssigneeName,
                newAssignee.getName()
        );

        outboxService.scheduleJob("WORK_ITEM_ASSIGNED", Map.of(
                "workItemId", saved.getId(),
                "assigneeEmail", newAssignee.getEmail(),
                "assignedBy", actor.getEmail()
        ));

        return WorkItemResponse.fromEntity(saved);
    }

    /**
     * Validates and executes state machine status transitions.
     * Enforces transition rules:
     * - OPEN -> IN_PROGRESS
     * - IN_PROGRESS -> AWAITING_APPROVAL
     * - AWAITING_APPROVAL -> RESOLVED (Requires LEAD or ADMIN role)
     * - RESOLVED -> CLOSED (Requires LEAD or ADMIN role)
     * - Direct jump to CLOSED or skipping approval throws 400 Bad Request.
     * - Non-Lead approval throws 403 Forbidden.
     */
    @Transactional
    public WorkItemResponse transitionStatus(UUID id, TransitionWorkItemRequest request) {
        log.info("Evaluating state machine transition for work item id={} to targetStatus={}", id, request.getTargetStatus());

        WorkItem item = getWorkItemEntity(id);
        User actor = userService.getUserEntity(request.getActorId());

        if (request.getExpectedVersion() != null && !item.getVersion().equals(request.getExpectedVersion())) {
            throw new ConflictException(
                    "OPTIMISTIC_LOCK_CONFLICT",
                    "Version mismatch detected for work item " + id + ". Current version: " + item.getVersion()
            );
        }

        Status current = item.getStatus();
        Status target = request.getTargetStatus();

        if (current == target) {
            log.info("Work item {} is already in status {}. No transition performed.", id, target);
            return WorkItemResponse.fromEntity(item);
        }

        // Validate state machine progression
        validateStateTransition(item, current, target, actor);

        item.setStatus(target);
        item.setUpdatedAt(LocalDateTime.now());
        WorkItem saved = workItemRepository.saveAndFlush(item);

        String description = (request.getComment() != null && !request.getComment().trim().isEmpty())
                ? request.getComment()
                : "Transitioned status from " + current + " to " + target;

        // Record audit activity
        activityAuditService.logActivity(
                saved,
                actor,
                ActionType.STATUS_CHANGED,
                description,
                current.name(),
                target.name()
        );

        // Schedule transactional outbox job
        outboxService.scheduleJob("WORK_ITEM_STATUS_CHANGED", Map.of(
                "workItemId", saved.getId(),
                "previousStatus", current.name(),
                "newStatus", target.name(),
                "actorEmail", actor.getEmail()
        ));

        return WorkItemResponse.fromEntity(saved);
    }

    /**
     * Enforces formal state machine transitions and role-based approval privileges.
     */
    private void validateStateTransition(WorkItem item, Status current, Status target, User actor) {
        // Disallow direct jump to CLOSED or skipping approval
        if (target == Status.CLOSED && current != Status.RESOLVED) {
            throw new InvalidWorkflowTransitionException(
                    "Direct jump to CLOSED from " + current + " is forbidden. Work items must follow the formal approval lifecycle: OPEN -> IN_PROGRESS -> AWAITING_APPROVAL -> RESOLVED -> CLOSED."
            );
        }

        // Validate allowed forward progression steps
        boolean isLegalTransition = false;

        switch (current) {
            case OPEN -> {
                if (target == Status.IN_PROGRESS) {
                    isLegalTransition = true;
                }
            }
            case IN_PROGRESS -> {
                if (target == Status.AWAITING_APPROVAL) {
                    isLegalTransition = true;
                }
            }
            case AWAITING_APPROVAL -> {
                if (target == Status.RESOLVED) {
                    isLegalTransition = true;
                } else if (target == Status.IN_PROGRESS) {
                    // Lead or Admin rejecting or requesting revision
                    isLegalTransition = true;
                }
            }
            case RESOLVED -> {
                if (target == Status.CLOSED) {
                    isLegalTransition = true;
                } else if (target == Status.IN_PROGRESS) {
                    // Reopening for remediation
                    isLegalTransition = true;
                }
            }
            case CLOSED -> {
                if (target == Status.OPEN) {
                    // Formal reopening by Lead/Admin
                    isLegalTransition = true;
                }
            }
        }

        if (!isLegalTransition) {
            throw new InvalidWorkflowTransitionException(
                    "Invalid workflow transition from " + current + " to " + target +
                            ". Allowed lifecycle sequence: OPEN -> IN_PROGRESS -> AWAITING_APPROVAL -> RESOLVED -> CLOSED."
            );
        }

        // Role authorization check for privileged transitions:
        // AWAITING_APPROVAL -> RESOLVED (Lead or Admin only)
        // RESOLVED -> CLOSED (Lead or Admin only)
        // CLOSED -> OPEN (Lead or Admin only)
        if (target == Status.RESOLVED || target == Status.CLOSED || current == Status.CLOSED) {
            boolean isLeadOrAdmin = teamService.isUserLeadOrAdmin(actor.getId(), item.getTeam().getId());
            if (!isLeadOrAdmin) {
                log.error("Access denied: User {} lacks LEAD or ADMIN role in team {} to perform transition to {}",
                        actor.getEmail(), item.getTeam().getCode(), target);
                throw new ForbiddenException(
                        "Approval requires Team Lead or Admin privilege."
                );
            }
        }
    }

    /**
     * Calculates operational dashboard statistics across all work items.
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        log.info("Calculating dashboard operational metrics");

        long total = workItemRepository.count();
        long open = workItemRepository.countByStatus(Status.OPEN);
        long inProgress = workItemRepository.countByStatus(Status.IN_PROGRESS);
        long awaitingApproval = workItemRepository.countByStatus(Status.AWAITING_APPROVAL);
        long resolved = workItemRepository.countByStatus(Status.RESOLVED);
        long closed = workItemRepository.countByStatus(Status.CLOSED);

        long critical = workItemRepository.countByPriority(Priority.CRITICAL);
        long high = workItemRepository.countByPriority(Priority.HIGH);
        long medium = workItemRepository.countByPriority(Priority.MEDIUM);
        long low = workItemRepository.countByPriority(Priority.LOW);

        long bug = workItemRepository.countByType(WorkItemType.BUG);
        long incident = workItemRepository.countByType(WorkItemType.INCIDENT);
        long task = workItemRepository.countByType(WorkItemType.TASK);
        long feature = workItemRepository.countByType(WorkItemType.FEATURE);

        Map<String, Long> statusBreakdown = Map.of(
                "OPEN", open,
                "IN_PROGRESS", inProgress,
                "AWAITING_APPROVAL", awaitingApproval,
                "RESOLVED", resolved,
                "CLOSED", closed
        );

        Map<String, Long> priorityBreakdown = Map.of(
                "CRITICAL", critical,
                "HIGH", high,
                "MEDIUM", medium,
                "LOW", low
        );

        Map<String, Long> typeBreakdown = Map.of(
                "BUG", bug,
                "INCIDENT", incident,
                "TASK", task,
                "FEATURE", feature
        );

        return DashboardStatsResponse.builder()
                .totalWorkItems(total)
                .openCount(open)
                .inProgressCount(inProgress)
                .awaitingApprovalCount(awaitingApproval)
                .resolvedCount(resolved)
                .closedCount(closed)
                .criticalPriorityCount(critical)
                .highPriorityCount(high)
                .mediumPriorityCount(medium)
                .lowPriorityCount(low)
                .bugTypeCount(bug)
                .incidentTypeCount(incident)
                .taskTypeCount(task)
                .featureTypeCount(feature)
                .statusBreakdown(statusBreakdown)
                .priorityBreakdown(priorityBreakdown)
                .typeBreakdown(typeBreakdown)
                .build();
    }
}
