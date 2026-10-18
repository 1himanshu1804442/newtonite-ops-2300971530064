package com.newtonite.ops.service;

import com.newtonite.ops.dto.ActivityEventResponse;
import com.newtonite.ops.model.entity.ActivityEvent;
import com.newtonite.ops.model.entity.User;
import com.newtonite.ops.model.entity.WorkItem;
import com.newtonite.ops.model.enums.ActionType;
import com.newtonite.ops.repository.ActivityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service managing audit log events for work item mutations.
 * Executes within existing transactional contexts to ensure atomic consistency with entity changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityAuditService {

    private final ActivityEventRepository activityEventRepository;

    /**
     * Records an audit event for a work item modification.
     * Propagation.MANDATORY ensures an active transaction must already exist from the caller.
     *
     * @param workItem The target work item
     * @param actor The operator initiating the mutation
     * @param actionType The type of action performed
     * @param description Human-readable description
     * @param previousState Previous state/value (optional)
     * @param newState New state/value (optional)
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public ActivityEvent logActivity(
            WorkItem workItem,
            User actor,
            ActionType actionType,
            String description,
            String previousState,
            String newState
    ) {
        log.info("Logging audit event: type={}, workItemId={}, actor={}", actionType, workItem.getId(), actor.getEmail());

        ActivityEvent event = ActivityEvent.builder()
                .workItem(workItem)
                .actor(actor)
                .actionType(actionType)
                .description(description)
                .previousState(previousState)
                .newState(newState)
                .createdAt(LocalDateTime.now())
                .build();

        return activityEventRepository.save(event);
    }

    /**
     * Retrieves the audit history for a specific work item.
     */
    @Transactional(readOnly = true)
    public List<ActivityEventResponse> getActivitiesForWorkItem(UUID workItemId) {
        log.info("Fetching audit activities for work item: {}", workItemId);
        return activityEventRepository.findByWorkItemIdOrderByCreatedAtDesc(workItemId)
                .stream()
                .map(ActivityEventResponse::fromEntity)
                .toList();
    }

    /**
     * Retrieves the most recent system-wide audit activities.
     */
    @Transactional(readOnly = true)
    public List<ActivityEventResponse> getRecentActivities() {
        log.info("Fetching recent system-wide audit activities");
        return activityEventRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(ActivityEventResponse::fromEntity)
                .toList();
    }
}
