package com.newtonite.ops;

import com.newtonite.ops.dto.ClaimWorkItemRequest;
import com.newtonite.ops.model.entity.WorkItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Concurrency & Atomic Ticket Claiming Test Suite.
 *
 * Operational Problem & Why This Approach:
 * In high-pressure operational triage environments, multiple engineers simultaneously monitor
 * the unassigned incident queue. When an urgent incident arrives, two or more engineers may click
 * "Claim" at the exact same millisecond.
 *
 * Under naive CRUD (read entity -> check if unassigned -> write assignee), both concurrent threads
 * read assignee == null and both proceed to write their user ID. The second write silently overwrites
 * the first without either engineer realizing, leading to duplicate investigations, conflicting actions,
 * and operational chaos.
 *
 * Newtonite Ops solves this by executing an atomic conditional database update:
 * "UPDATE work_items SET assignee_id = :userId, version = version + 1 WHERE id = :id AND assignee_id IS NULL"
 * PostgreSQL row-level MVCC guarantees that exactly one transaction succeeds (updating 1 row),
 * while subsequent concurrent transactions find 0 affected rows and immediately return HTTP 409 Conflict.
 */
public class ConcurrencyAndClaimTests extends BaseIntegrationTest {

    @Test
    @DisplayName("Simultaneous concurrent claim by 2 threads: exactly 1 succeeds (200 OK) and 1 receives 409 Conflict")
    public void testTwoThreadsConcurrentClaimRaceCondition() throws Exception {
        // 1. Arrange: Create an unassigned incident in Platform Engineering
        WorkItem unassignedItem = createUnassignedWorkItem(
                platformTeam,
                alexAdmin,
                "INC-501: Critical Database Connection Pool Starvation"
        );
        UUID itemId = unassignedItem.getId();

        // 2. Setup 2 parallel threads attempting to claim simultaneously:
        // Thread 1: Elena Rostova (Lead)
        // Thread 2: Marcus Chen (Member)
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // startGate ensures both threads are spawned, initialized, and release simultaneously at the exact same instant
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        List<String> responseBodies = Collections.synchronizedList(new ArrayList<>());

        UUID[] candidateUsers = new UUID[]{elenaLead.getId(), marcusMember.getId()};

        for (int i = 0; i < threadCount; i++) {
            final UUID claimingUserId = candidateUsers[i];
            executor.submit(() -> {
                try {
                    // Wait at the starting gate for synchronized release
                    startGate.await();

                    ClaimWorkItemRequest request = ClaimWorkItemRequest.builder()
                            .userId(claimingUserId)
                            .build();

                    MvcResult result = mockMvc.perform(post("/api/work-items/" + itemId + "/claim")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    statusCodes.add(result.getResponse().getStatus());
                    responseBodies.add(result.getResponse().getContentAsString());
                } catch (Exception e) {
                    statusCodes.add(500);
                    responseBodies.add(e.getMessage());
                } finally {
                    finishGate.countDown();
                }
            });
        }

        // Fire both threads at the exact same instant
        startGate.countDown();

        // Wait up to 10 seconds for both requests to complete
        boolean finishedInTime = finishGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finishedInTime).as("All concurrent claim threads must finish execution in time").isTrue();

        // 3. Assert HTTP status code distribution:
        // Exactly ONE request must receive HTTP 200 OK
        // Exactly ONE request must receive HTTP 409 Conflict
        long okCount = statusCodes.stream().filter(s -> s == 200).count();
        long conflictCount = statusCodes.stream().filter(s -> s == 409).count();

        assertThat(okCount).as("Exactly one claimant must succeed with HTTP 200 OK").isEqualTo(1);
        assertThat(conflictCount).as("The concurrent claimant must receive HTTP 409 Conflict").isEqualTo(1);

        // 4. Assert 409 Conflict response payload details
        String conflictPayload = responseBodies.stream()
                .filter(b -> b.contains("CONFLICT") || b.contains("already been claimed"))
                .findFirst()
                .orElse("");
        assertThat(conflictPayload).contains("CONCURRENT_CLAIM_CONFLICT");
        assertThat(conflictPayload).contains("already been claimed");

        // 5. Assert Database State Integrity:
        // Item must now have an assignee, and version must be incremented by 1
        WorkItem refreshed = workItemRepository.findById(itemId).orElseThrow();
        assertThat(refreshed.getAssignee()).isNotNull();
        assertThat(refreshed.getVersion()).isEqualTo(1L);

        // Assignee must match the claimant who received HTTP 200 OK
        String successfulUserEmail = refreshed.getAssignee().getEmail();
        assertThat(successfulUserEmail).isIn("elena.lead@newtonite.io", "marcus.member@newtonite.io");
    }

    @Test
    @DisplayName("High contention claim by 6 concurrent threads: exactly 1 succeeds and 5 receive 409 Conflict")
    public void testHighContentionSixConcurrentClaims() throws Exception {
        // Arrange
        WorkItem unassignedItem = createUnassignedWorkItem(
                platformTeam,
                alexAdmin,
                "INC-702: High-Severity Memory Leak in Ingestion Pipeline"
        );
        UUID itemId = unassignedItem.getId();

        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);

        AtomicInteger okCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Use Marcus Chen and Elena Rostova as valid team members
        for (int i = 0; i < threadCount; i++) {
            final UUID userId = (i % 2 == 0) ? elenaLead.getId() : marcusMember.getId();
            executor.submit(() -> {
                try {
                    startGate.await();

                    ClaimWorkItemRequest request = ClaimWorkItemRequest.builder()
                            .userId(userId)
                            .build();

                    MvcResult result = mockMvc.perform(post("/api/work-items/" + itemId + "/claim")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        okCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown();
        finishGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 winner under high contention
        assertThat(okCount.get()).as("Under high contention, exactly 1 claimer must succeed").isEqualTo(1);
        assertThat(conflictCount.get()).as("All 5 other claimers must be rejected with 409 Conflict").isEqualTo(5);

        // Database row must be firmly assigned
        WorkItem saved = workItemRepository.findById(itemId).orElseThrow();
        assertThat(saved.getAssignee()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Sequential claim attempt on an already claimed work item returns HTTP 409 Conflict")
    public void testSequentialClaimOnAlreadyClaimedWorkItem() throws Exception {
        // 1. Arrange: Create work item and claim it with Elena Rostova
        WorkItem item = createUnassignedWorkItem(platformTeam, alexAdmin, "INC-303: Auth Service Latency Spike");
        UUID itemId = item.getId();

        ClaimWorkItemRequest initialClaim = ClaimWorkItemRequest.builder()
                .userId(elenaLead.getId())
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialClaim)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.id").value(elenaLead.getId().toString()));

        // 2. Act: Marcus Chen tries to claim the already claimed item sequentially
        ClaimWorkItemRequest subsequentClaim = ClaimWorkItemRequest.builder()
                .userId(marcusMember.getId())
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subsequentClaim)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_CLAIM_CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Elena Rostova")));

        // 3. Assert: Database assignee remains Elena Rostova and version remains 1
        WorkItem refreshed = workItemRepository.findById(itemId).orElseThrow();
        assertThat(refreshed.getAssignee().getId()).isEqualTo(elenaLead.getId());
        assertThat(refreshed.getVersion()).isEqualTo(1L);
    }
}
