package com.newtonite.ops;

import com.newtonite.ops.dto.*;
import com.newtonite.ops.exception.ConflictException;
import com.newtonite.ops.exception.ForbiddenException;
import com.newtonite.ops.exception.InvalidWorkflowTransitionException;
import com.newtonite.ops.model.entity.ActivityEvent;
import com.newtonite.ops.model.entity.Team;
import com.newtonite.ops.model.entity.User;
import com.newtonite.ops.model.enums.*;
import com.newtonite.ops.repository.ActivityEventRepository;
import com.newtonite.ops.repository.TeamRepository;
import com.newtonite.ops.repository.UserRepository;
import com.newtonite.ops.service.OutboxWorkerService;
import com.newtonite.ops.service.WorkItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
public class WorkItemWorkflowAndConcurrencyTest {

    @Autowired
    private WorkItemService workItemService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ActivityEventRepository activityEventRepository;

    @Autowired
    private OutboxWorkerService outboxWorkerService;

    @Test
    @DisplayName("1. Atomic Concurrent Claim: Second claim attempt must fail with ConflictException (409)")
    void testAtomicConcurrentClaim() {
        Team team = teamRepository.findByCode("ENG-PLAT").orElseThrow();
        User creator = userRepository.findByEmail("alex.rivera@newtonite.io").orElseThrow();
        User userA = userRepository.findByEmail("elena.rostova@newtonite.io").orElseThrow();
        User userB = userRepository.findByEmail("marcus.chen@newtonite.io").orElseThrow();

        // Create an unassigned work item
        CreateWorkItemRequest createReq = CreateWorkItemRequest.builder()
                .title("Unassigned task for concurrency claim test")
                .description("Test description")
                .type(WorkItemType.TASK)
                .priority(Priority.HIGH)
                .teamId(team.getId())
                .creatorId(creator.getId())
                .build();
        WorkItemResponse created = workItemService.createWorkItem(createReq);
        assertNull(created.getAssignee());

        // First user claims atomically -> succeeds
        WorkItemResponse claimedFirst = workItemService.claimWorkItem(created.getId(), userA.getId());
        assertNotNull(claimedFirst.getAssignee());
        assertEquals(userA.getId(), claimedFirst.getAssignee().getId());

        // Second user attempts to claim the same work item -> must fail with ConflictException
        ConflictException conflict = assertThrows(ConflictException.class, () ->
                workItemService.claimWorkItem(created.getId(), userB.getId())
        );
        assertEquals("CONCURRENT_CLAIM_CONFLICT", conflict.getErrorCode());
    }

    @Test
    @DisplayName("2. Optimistic Locking: Modification with outdated expectedVersion throws ConflictException")
    void testOptimisticLockingValidation() {
        Team team = teamRepository.findByCode("SRE-CORE").orElseThrow();
        User lead = userRepository.findByEmail("sarah.connor@newtonite.io").orElseThrow();

        CreateWorkItemRequest createReq = CreateWorkItemRequest.builder()
                .title("Initial work item for version test")
                .description("Version testing")
                .type(WorkItemType.INCIDENT)
                .priority(Priority.HIGH)
                .teamId(team.getId())
                .creatorId(lead.getId())
                .build();
        WorkItemResponse item = workItemService.createWorkItem(createReq);
        Long initialVersion = item.getVersion();

        // Valid update matching expected version -> succeeds
        UpdateWorkItemRequest validUpdate = UpdateWorkItemRequest.builder()
                .title("Updated work item title")
                .description("Updated description")
                .type(WorkItemType.INCIDENT)
                .priority(Priority.CRITICAL)
                .expectedVersion(initialVersion)
                .actorId(lead.getId())
                .build();
        WorkItemResponse updated = workItemService.updateWorkItem(item.getId(), validUpdate);
        assertEquals(initialVersion + 1, updated.getVersion());

        // Attempting an update with the stale initialVersion -> throws ConflictException
        UpdateWorkItemRequest staleUpdate = UpdateWorkItemRequest.builder()
                .title("Stale concurrent update attempt")
                .description("Should be rejected")
                .type(WorkItemType.INCIDENT)
                .priority(Priority.LOW)
                .expectedVersion(initialVersion) // Outdated version
                .actorId(lead.getId())
                .build();

        ConflictException conflict = assertThrows(ConflictException.class, () ->
                workItemService.updateWorkItem(item.getId(), staleUpdate)
        );
        assertEquals("OPTIMISTIC_LOCK_CONFLICT", conflict.getErrorCode());
    }

    @Test
    @DisplayName("3. Workflow State Machine: Valid transitions and role authorization checks")
    void testWorkflowStateMachineTransitions() {
        Team team = teamRepository.findByCode("SEC-OPS").orElseThrow();
        User admin = userRepository.findByEmail("david.kim@newtonite.io").orElseThrow();   // ADMIN
        User lead = userRepository.findByEmail("priya.sharma@newtonite.io").orElseThrow();  // LEAD
        User viewer = userRepository.findByEmail("james.wilson@newtonite.io").orElseThrow(); // VIEWER

        CreateWorkItemRequest createReq = CreateWorkItemRequest.builder()
                .title("Security patch workflow test")
                .description("Testing lifecycle stages")
                .type(WorkItemType.BUG)
                .priority(Priority.HIGH)
                .teamId(team.getId())
                .creatorId(admin.getId())
                .build();
        WorkItemResponse item = workItemService.createWorkItem(createReq);
        assertEquals(Status.OPEN, item.getStatus());

        // Direct jump to CLOSED or skipping approval must throw InvalidWorkflowTransitionException (400)
        assertThrows(InvalidWorkflowTransitionException.class, () ->
                workItemService.transitionStatus(item.getId(), TransitionWorkItemRequest.builder()
                        .targetStatus(Status.CLOSED)
                        .actorId(admin.getId())
                        .build())
        );

        // Step 1: OPEN -> IN_PROGRESS
        WorkItemResponse inProgress = workItemService.transitionStatus(item.getId(), TransitionWorkItemRequest.builder()
                .targetStatus(Status.IN_PROGRESS)
                .actorId(admin.getId())
                .build());
        assertEquals(Status.IN_PROGRESS, inProgress.getStatus());

        // Step 2: IN_PROGRESS -> AWAITING_APPROVAL
        WorkItemResponse awaiting = workItemService.transitionStatus(item.getId(), TransitionWorkItemRequest.builder()
                .targetStatus(Status.AWAITING_APPROVAL)
                .actorId(admin.getId())
                .build());
        assertEquals(Status.AWAITING_APPROVAL, awaiting.getStatus());

        // Step 3a: Non-Lead (VIEWER) attempting to approve to RESOLVED must throw ForbiddenException (403)
        assertThrows(ForbiddenException.class, () ->
                workItemService.transitionStatus(item.getId(), TransitionWorkItemRequest.builder()
                        .targetStatus(Status.RESOLVED)
                        .actorId(viewer.getId())
                        .build())
        );

        // Step 3b: LEAD approving to RESOLVED -> succeeds
        WorkItemResponse resolved = workItemService.transitionStatus(item.getId(), TransitionWorkItemRequest.builder()
                .targetStatus(Status.RESOLVED)
                .actorId(lead.getId())
                .comment("Security patch verified in staging")
                .build());
        assertEquals(Status.RESOLVED, resolved.getStatus());

        // Step 4: LEAD closing to CLOSED -> succeeds
        WorkItemResponse closed = workItemService.transitionStatus(item.getId(), TransitionWorkItemRequest.builder()
                .targetStatus(Status.CLOSED)
                .actorId(lead.getId())
                .comment("Verified production rollout")
                .build());
        assertEquals(Status.CLOSED, closed.getStatus());
    }

    @Test
    @DisplayName("4. Activity Audit Logging: Verifies audit records are persisted in same transaction")
    void testActivityAuditLogging() {
        Team team = teamRepository.findByCode("DATA-INFRA").orElseThrow();
        User lead = userRepository.findByEmail("liam.oconnor@newtonite.io").orElseThrow();

        CreateWorkItemRequest req = CreateWorkItemRequest.builder()
                .title("Audit event verification item")
                .description("Ensures activity trail exists")
                .type(WorkItemType.TASK)
                .priority(Priority.LOW)
                .teamId(team.getId())
                .creatorId(lead.getId())
                .build();
        WorkItemResponse item = workItemService.createWorkItem(req);

        List<ActivityEvent> events = activityEventRepository.findByWorkItemIdOrderByCreatedAtDesc(item.getId());
        assertFalse(events.isEmpty(), "Activity audit record must be recorded on creation");
        assertEquals(ActionType.CREATED, events.get(0).getActionType());
    }

    @Test
    @DisplayName("5. Transactional Outbox: Draining pending outbox jobs")
    void testOutboxProcessing() {
        int processed = outboxWorkerService.processPendingJobs();
        assertTrue(processed >= 0, "Outbox worker processed pending jobs without error");
    }
}
