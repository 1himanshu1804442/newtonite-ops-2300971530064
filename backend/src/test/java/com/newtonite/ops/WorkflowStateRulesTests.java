package com.newtonite.ops;

import com.newtonite.ops.dto.TransitionWorkItemRequest;
import com.newtonite.ops.model.entity.ActivityEvent;
import com.newtonite.ops.model.entity.WorkItem;
import com.newtonite.ops.model.enums.ActionType;
import com.newtonite.ops.model.enums.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Domain Workflow State Machine & Server-Enforced RBAC Test Suite.
 *
 * Operational Problem & Why This Approach:
 * Operational work involves strict compliance, auditing, and separation-of-duties boundaries.
 * In a regulatory or incident-response environment:
 * 1. An incident or critical bug cannot jump directly from OPEN to CLOSED without undergoing diagnosis
 *    (IN_PROGRESS) and senior verification (AWAITING_APPROVAL).
 * 2. An item in AWAITING_APPROVAL cannot be approved or resolved by the junior engineer who worked on it.
 *    Four-eyes approval requires sign-off from a Team Lead or Admin.
 *
 * Client-side button disabling or UI-only route hiding is completely insecure; any operator can open
 * browser DevTools or use curl to fire PATCH / POST requests directly to the API.
 *
 * Newtonite Ops enforces a strict server-side state machine:
 * - Allowed graph: OPEN -> IN_PROGRESS -> AWAITING_APPROVAL -> RESOLVED -> CLOSED.
 * - Illegal jumps (e.g., OPEN -> CLOSED) are rejected with HTTP 400 Bad Request.
 * - Privileged transitions (to RESOLVED and CLOSED) verify team role and return HTTP 403 Forbidden
 *   if executed by a standard MEMBER without LEAD or ADMIN authority.
 */
public class WorkflowStateRulesTests extends BaseIntegrationTest {

    @Test
    @DisplayName("Forbidden state transition: Direct jump from OPEN to CLOSED returns HTTP 400 Bad Request")
    public void testForbiddenDirectJumpOpenToClosed() throws Exception {
        // 1. Arrange: Create work item in OPEN state
        WorkItem item = createUnassignedWorkItem(
                platformTeam,
                alexAdmin,
                "INC-801: Unchecked Storage Growth in Kafka Broker Log Dirs"
        );
        UUID itemId = item.getId();
        assertThat(item.getStatus()).isEqualTo(Status.OPEN);

        // 2. Act: Attempt direct transition to CLOSED without diagnosis or approval
        TransitionWorkItemRequest illegalJump = TransitionWorkItemRequest.builder()
                .targetStatus(Status.CLOSED)
                .actorId(elenaLead.getId())
                .comment("Attempting illegal jump straight from OPEN to CLOSED")
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalJump)))
                .andExpect(status().isBadRequest()) // HTTP 400 Bad Request
                .andExpect(jsonPath("$.errorCode").value("INVALID_WORKFLOW_TRANSITION"))
                .andExpect(jsonPath("$.message").value(containsString("Direct jump to CLOSED from OPEN is forbidden")));

        // 3. Assert: Database status is unchanged (remains OPEN)
        WorkItem refreshed = workItemRepository.findById(itemId).orElseThrow();
        assertThat(refreshed.getStatus()).as("Status must remain OPEN").isEqualTo(Status.OPEN);
    }

    @Test
    @DisplayName("Forbidden state transition: Direct jump from OPEN to RESOLVED returns HTTP 400 Bad Request")
    public void testForbiddenDirectJumpOpenToResolved() throws Exception {
        WorkItem item = createUnassignedWorkItem(
                platformTeam,
                alexAdmin,
                "INC-802: Database Connection Timeout on Slave Replicas"
        );
        UUID itemId = item.getId();

        TransitionWorkItemRequest illegalJump = TransitionWorkItemRequest.builder()
                .targetStatus(Status.RESOLVED)
                .actorId(elenaLead.getId())
                .comment("Attempting illegal jump skipping IN_PROGRESS and AWAITING_APPROVAL")
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalJump)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_WORKFLOW_TRANSITION"))
                .andExpect(jsonPath("$.message").value(containsString("Invalid workflow transition from OPEN to RESOLVED")));

        WorkItem refreshed = workItemRepository.findById(itemId).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(Status.OPEN);
    }

    @Test
    @DisplayName("Role-restricted transition: MEMBER resolving AWAITING_APPROVAL gets 403 Forbidden; LEAD gets 200 OK")
    public void testRoleRestrictedTransitionAwaitingApprovalToResolved() throws Exception {
        // 1. Arrange: Create work item and advance it to AWAITING_APPROVAL
        WorkItem item = createAssignedWorkItem(
                platformTeam,
                alexAdmin,
                marcusMember,
                Status.OPEN,
                "INC-803: Memory Leak in Webhook Dispatch Service"
        );
        UUID itemId = item.getId();

        // Step 1: OPEN -> IN_PROGRESS (Marcus Member) -> 200 OK
        TransitionWorkItemRequest startProgress = TransitionWorkItemRequest.builder()
                .targetStatus(Status.IN_PROGRESS)
                .actorId(marcusMember.getId())
                .comment("Starting root cause investigation")
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startProgress)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Step 2: IN_PROGRESS -> AWAITING_APPROVAL (Marcus Member) -> 200 OK
        TransitionWorkItemRequest submitForApproval = TransitionWorkItemRequest.builder()
                .targetStatus(Status.AWAITING_APPROVAL)
                .actorId(marcusMember.getId())
                .comment("Identified unclosed byte buffers. Patch deployed to staging. Requesting approval.")
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitForApproval)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"));

        WorkItem awaitingItem = workItemRepository.findById(itemId).orElseThrow();
        assertThat(awaitingItem.getStatus()).isEqualTo(Status.AWAITING_APPROVAL);

        // 2. Act & Assert: Marcus Chen (role: MEMBER) attempts to self-approve to RESOLVED
        // Must be rejected with HTTP 403 Forbidden!
        TransitionWorkItemRequest memberResolveAttempt = TransitionWorkItemRequest.builder()
                .targetStatus(Status.RESOLVED)
                .actorId(marcusMember.getId()) // MEMBER role
                .comment("Attempting self-resolution as junior engineer")
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memberResolveAttempt)))
                .andExpect(status().isForbidden()) // HTTP 403 Forbidden
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_ROLE_PERMISSIONS"))
                .andExpect(jsonPath("$.message").value(containsString("Approval requires Team Lead or Admin privilege.")));

        // Verify database: status remains AWAITING_APPROVAL
        WorkItem unapproved = workItemRepository.findById(itemId).orElseThrow();
        assertThat(unapproved.getStatus()).as("Status must not advance on forbidden transition").isEqualTo(Status.AWAITING_APPROVAL);

        // 3. Act & Assert: Elena Rostova (role: LEAD) approves transition to RESOLVED
        // Must succeed with HTTP 200 OK!
        TransitionWorkItemRequest leadResolve = TransitionWorkItemRequest.builder()
                .targetStatus(Status.RESOLVED)
                .actorId(elenaLead.getId()) // LEAD role
                .comment("Reviewed heap dumps and staging validation metrics. Fix verified. Approved.")
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leadResolve)))
                .andExpect(status().isOk()) // HTTP 200 OK
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Verify database: status is now RESOLVED
        WorkItem resolved = workItemRepository.findById(itemId).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(Status.RESOLVED);

        // Step 4: Elena Rostova (LEAD) closes the item
        TransitionWorkItemRequest leadClose = TransitionWorkItemRequest.builder()
                .targetStatus(Status.CLOSED)
                .actorId(elenaLead.getId())
                .comment("Closing ticket post-incident review.")
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leadClose)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        WorkItem closed = workItemRepository.findById(itemId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(Status.CLOSED);

        // Verify audit trail logged all state transitions
        List<ActivityEvent> events = activityEventRepository.findByWorkItemIdOrderByCreatedAtDesc(itemId);
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> e.getActionType() == ActionType.STATUS_CHANGED)).isTrue();
    }
}
