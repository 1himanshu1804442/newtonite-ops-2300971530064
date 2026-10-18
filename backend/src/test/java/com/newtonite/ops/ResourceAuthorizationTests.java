package com.newtonite.ops;

import com.newtonite.ops.dto.ClaimWorkItemRequest;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resource Authorization & Team-Level Isolation Test Suite.
 *
 * Operational Problem & Why This Approach:
 * In modern multi-tenant and multi-team engineering organizations (e.g. Platform Engineering vs. Site Reliability Engineering),
 * work items contain sensitive internal diagnostics, architectural credentials, and team-specific incident reviews.
 *
 * Without server-enforced resource authorization boundaries:
 * 1. An engineer from Team B could inadvertently view, triage, or claim an active incident in Team A's queue.
 * 2. Unauthorized edits from outside team members would compromise operational accountability and corrupt SLA tracking.
 *
 * Newtonite Ops enforces strict team-level isolation:
 * - A user who is not an enrolled member of the item's owning team is rejected with HTTP 403 Forbidden
 *   when attempting to view, edit, claim, or transition work items.
 * - This provides compile-time and runtime security guarantees independent of client-side UI filters.
 */
public class ResourceAuthorizationTests extends BaseIntegrationTest {

    @Test
    @DisplayName("User outside team cannot view work item: GET returns HTTP 403 Forbidden")
    public void testUserOutsideTeamCannotViewWorkItem() throws Exception {
        // 1. Arrange: Create a work item belonging exclusively to Platform Engineering (Team 1)
        WorkItem platformItem = createAssignedWorkItem(
                platformTeam,
                alexAdmin,
                marcusMember,
                Status.IN_PROGRESS,
                "SEC-101: Rotate Production TLS Root Certificates"
        );
        UUID itemId = platformItem.getId();

        // Chloe Martin belongs strictly to SRE Team (Team 2), NOT Platform Engineering
        UUID outsideUserId = chloeMember.getId();

        // 2. Act & Assert: Chloe attempts to view Team 1's item via query parameter
        mockMvc.perform(get("/api/work-items/" + itemId)
                        .param("actorId", outsideUserId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()) // HTTP 403 Forbidden
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_ROLE_PERMISSIONS"))
                .andExpect(jsonPath("$.message").value(containsString("is not a member of team 'Platform Engineering'")));

        // 3. Act & Assert: Chloe attempts to view Team 1's item via X-User-Id request header
        mockMvc.perform(get("/api/work-items/" + itemId)
                        .header("X-User-Id", outsideUserId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_ROLE_PERMISSIONS"));
    }

    @Test
    @DisplayName("User inside team can view work item: GET returns HTTP 200 OK")
    public void testUserInsideTeamCanViewWorkItem() throws Exception {
        // Arrange
        WorkItem platformItem = createAssignedWorkItem(
                platformTeam,
                alexAdmin,
                marcusMember,
                Status.IN_PROGRESS,
                "TASK-303: Migrate Kubernetes Ingress to Gateway API"
        );
        UUID itemId = platformItem.getId();

        // Marcus Chen belongs to Platform Engineering (Team 1)
        UUID insideUserId = marcusMember.getId();

        // Act & Assert
        mockMvc.perform(get("/api/work-items/" + itemId)
                        .param("actorId", insideUserId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // HTTP 200 OK
                .andExpect(jsonPath("$.id").value(itemId.toString()))
                .andExpect(jsonPath("$.team.code").value("ENG-PLAT"));
    }

    @Test
    @DisplayName("User outside team cannot edit work item: PATCH returns HTTP 403 Forbidden")
    public void testUserOutsideTeamCannotEditWorkItem() throws Exception {
        // Arrange
        WorkItem platformItem = createAssignedWorkItem(
                platformTeam,
                alexAdmin,
                marcusMember,
                Status.IN_PROGRESS,
                "INC-404: Zero-Day Vulnerability Patching"
        );
        UUID itemId = platformItem.getId();

        // Chloe Martin belongs to SRE Team (Team 2)
        UpdateWorkItemRequest unauthorizedEdit = UpdateWorkItemRequest.builder()
                .title("HACKED: Unauthorized Title Overwrite")
                .description("Malicious modification attempt from outside team.")
                .type(WorkItemType.INCIDENT)
                .priority(Priority.LOW)
                .expectedVersion(0L)
                .actorId(chloeMember.getId()) // Outside actor!
                .build();

        // Act & Assert
        mockMvc.perform(patch("/api/work-items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unauthorizedEdit)))
                .andExpect(status().isForbidden()) // HTTP 403 Forbidden
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_ROLE_PERMISSIONS"))
                .andExpect(jsonPath("$.message").value(containsString("is not a member of team 'Platform Engineering'")));

        // Assert DB state: Entity title and priority were NOT compromised
        WorkItem preserved = workItemRepository.findById(itemId).orElseThrow();
        assertThat(preserved.getTitle()).isEqualTo("INC-404: Zero-Day Vulnerability Patching");
        assertThat(preserved.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("User outside team cannot claim work item: POST /claim returns HTTP 403 Forbidden")
    public void testUserOutsideTeamCannotClaimWorkItem() throws Exception {
        // Arrange: Unassigned work item in Platform Engineering
        WorkItem unassignedItem = createUnassignedWorkItem(
                platformTeam,
                alexAdmin,
                "INC-405: Core Switch Packet Loss Investigation"
        );
        UUID itemId = unassignedItem.getId();

        // Chloe Martin belongs to SRE Team
        ClaimWorkItemRequest unauthorizedClaim = ClaimWorkItemRequest.builder()
                .userId(chloeMember.getId())
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/work-items/" + itemId + "/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unauthorizedClaim)))
                .andExpect(status().isForbidden()) // HTTP 403 Forbidden
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_ROLE_PERMISSIONS"))
                .andExpect(jsonPath("$.message").value(containsString("is not a member of team 'Platform Engineering'")));

        // Assert DB state: Work item remains unassigned
        WorkItem refreshed = workItemRepository.findById(itemId).orElseThrow();
        assertThat(refreshed.getAssignee()).isNull();
        assertThat(refreshed.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("User inside team can edit work item: PATCH returns HTTP 200 OK")
    public void testUserInsideTeamCanEditWorkItem() throws Exception {
        // Arrange
        WorkItem item = createAssignedWorkItem(
                platformTeam,
                alexAdmin,
                marcusMember,
                Status.IN_PROGRESS,
                "TASK-505: Helm Value Secrets Rotation"
        );
        UUID itemId = item.getId();

        // Elena Rostova is Team Lead in Platform Engineering
        UpdateWorkItemRequest authorizedEdit = UpdateWorkItemRequest.builder()
                .title("TASK-505: Helm Value Secrets Rotation (Approved Spec)")
                .description("Validated secret rotation scripts.")
                .type(WorkItemType.TASK)
                .priority(Priority.HIGH)
                .expectedVersion(0L)
                .actorId(elenaLead.getId())
                .build();

        // Act & Assert
        mockMvc.perform(patch("/api/work-items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authorizedEdit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("TASK-505: Helm Value Secrets Rotation (Approved Spec)"))
                .andExpect(jsonPath("$.version").value(1));

        WorkItem updated = workItemRepository.findById(itemId).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("TASK-505: Helm Value Secrets Rotation (Approved Spec)");
    }
}
