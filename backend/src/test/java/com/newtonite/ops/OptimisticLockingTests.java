package com.newtonite.ops;

import com.newtonite.ops.dto.UpdateWorkItemRequest;
import com.newtonite.ops.model.entity.WorkItem;
import com.newtonite.ops.model.enums.Priority;
import com.newtonite.ops.model.enums.Status;
import com.newtonite.ops.model.enums.WorkItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Optimistic Concurrency Control (OCC) & Stale Edits Test Suite.
 *
 * Operational Problem & Why This Approach:
 * In enterprise incident management, operational engineers spend minutes reading tickets,
 * diagnosing logs, and drafting remediation steps ("human think time").
 *
 * If Engineer A and Engineer B both open the same incident when version = 0:
 * 1. Engineer A quickly raises the priority to CRITICAL and updates title to reflect production outage (version advances to 1).
 * 2. Two minutes later, Engineer B submits their draft changes based on their original stale view of version = 0.
 *
 * Under Last-Write-Wins (LWW), Engineer B's write silently obliterates Engineer A's critical updates (the classic "Lost Update" problem).
 * Holding pessimistic row locks (`SELECT FOR UPDATE`) across human think time is impossible as it would tie up database connections
 * and cause connection pool exhaustion.
 *
 * Newtonite Ops solves this with Optimistic Concurrency Control:
 * Every mutating PATCH request must supply the version it read (`expectedVersion`).
 * If the database version has advanced beyond the client's expected version, the server throws an
 * optimistic locking conflict (HTTP 409 Conflict) and returns the LATEST entity state in the 'details' field,
 * enabling the client UI to immediately reconcile differences without data loss.
 */
public class OptimisticLockingTests extends BaseIntegrationTest {

    @Test
    @DisplayName("Stale update with outdated version receives HTTP 409 Conflict and latest entity state")
    public void testStaleUpdateHandlingReceivesConflictAndLatestEntityState() throws Exception {
        // 1. Arrange: Create work item in Platform Engineering with initial version = 0
        WorkItem originalItem = createAssignedWorkItem(
                platformTeam,
                alexAdmin,
                marcusMember,
                Status.IN_PROGRESS,
                "INC-101: Redis Cluster Node Failover In Progress"
        );
        UUID itemId = originalItem.getId();
        assertThat(originalItem.getVersion()).isEqualTo(0L);

        // 2. Client A submits a valid update based on version = 0
        UpdateWorkItemRequest clientAUpdate = UpdateWorkItemRequest.builder()
                .title("INC-101: Redis Cluster Master Demoted - Elevating Priority")
                .description("Cluster master lost quorum; failover node promoted.")
                .type(WorkItemType.INCIDENT)
                .priority(Priority.CRITICAL) // Elevated to CRITICAL by Client A
                .expectedVersion(0L)         // Matches current database version
                .actorId(elenaLead.getId())
                .build();

        mockMvc.perform(patch("/api/work-items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientAUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.title").value("INC-101: Redis Cluster Master Demoted - Elevating Priority"));

        // Verify database: version is now 1
        WorkItem afterClientA = workItemRepository.findById(itemId).orElseThrow();
        assertThat(afterClientA.getVersion()).isEqualTo(1L);
        assertThat(afterClientA.getPriority()).isEqualTo(Priority.CRITICAL);

        // 3. Act: Client B (who still holds outdated version = 0) submits an update
        UpdateWorkItemRequest clientBStaleUpdate = UpdateWorkItemRequest.builder()
                .title("INC-101: Stale Title Submission by Client B")
                .description("Client B did not see Client A's priority elevation.")
                .type(WorkItemType.INCIDENT)
                .priority(Priority.LOW) // Stale overwrite attempt!
                .expectedVersion(0L)    // STALE VERSION: Database is at version 1!
                .actorId(marcusMember.getId())
                .build();

        mockMvc.perform(patch("/api/work-items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientBStaleUpdate)))
                .andExpect(status().isConflict()) // HTTP 409 Conflict
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"))
                .andExpect(jsonPath("$.message").value(containsString("Version mismatch detected")))
                // Critical requirement: HTTP 409 response contains the LATEST entity state in details
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.version").value(1))
                .andExpect(jsonPath("$.details.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.details.title").value("INC-101: Redis Cluster Master Demoted - Elevating Priority"));

        // 4. Assert: Database entity was NOT modified by Client B's stale update
        WorkItem afterClientB = workItemRepository.findById(itemId).orElseThrow();
        assertThat(afterClientB.getVersion()).as("Version must remain 1 after stale update was rejected").isEqualTo(1L);
        assertThat(afterClientB.getPriority()).as("Priority must preserve Client A's update (CRITICAL)").isEqualTo(Priority.CRITICAL);
        assertThat(afterClientB.getTitle()).as("Title must preserve Client A's update").isEqualTo("INC-101: Redis Cluster Master Demoted - Elevating Priority");
    }

    @Test
    @DisplayName("Reconciled update with fresh expected version succeeds and advances version to 2")
    public void testReconciledUpdateWithFreshVersionSucceeds() throws Exception {
        // Arrange
        WorkItem item = createAssignedWorkItem(
                platformTeam,
                alexAdmin,
                marcusMember,
                Status.IN_PROGRESS,
                "TASK-202: Upgrade Helm Chart Definitions"
        );
        UUID itemId = item.getId();

        // 1. Initial update advances version from 0 to 1
        UpdateWorkItemRequest firstUpdate = UpdateWorkItemRequest.builder()
                .title("TASK-202: Upgraded Chart to v2.4.0")
                .description("Initial chart update completed.")
                .type(WorkItemType.TASK)
                .priority(Priority.MEDIUM)
                .expectedVersion(0L)
                .actorId(marcusMember.getId())
                .build();

        mockMvc.perform(patch("/api/work-items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // 2. Client uses fresh version = 1 for subsequent update
        UpdateWorkItemRequest secondUpdate = UpdateWorkItemRequest.builder()
                .title("TASK-202: Upgraded Chart to v2.4.1 (Bugfix Patch)")
                .description("Applied security patch release.")
                .type(WorkItemType.TASK)
                .priority(Priority.HIGH)
                .expectedVersion(1L) // Fresh version matching DB
                .actorId(elenaLead.getId())
                .build();

        mockMvc.perform(patch("/api/work-items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.title").value("TASK-202: Upgraded Chart to v2.4.1 (Bugfix Patch)"))
                .andExpect(jsonPath("$.priority").value("HIGH"));

        // Assert DB state
        WorkItem refreshed = workItemRepository.findById(itemId).orElseThrow();
        assertThat(refreshed.getVersion()).isEqualTo(2L);
        assertThat(refreshed.getTitle()).isEqualTo("TASK-202: Upgraded Chart to v2.4.1 (Bugfix Patch)");
    }
}
