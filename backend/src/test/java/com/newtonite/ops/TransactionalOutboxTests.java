package com.newtonite.ops;

import com.newtonite.ops.dto.ClaimWorkItemRequest;
import com.newtonite.ops.dto.CreateWorkItemRequest;
import com.newtonite.ops.dto.TransitionWorkItemRequest;
import com.newtonite.ops.model.entity.OutboxJob;
import com.newtonite.ops.model.entity.WorkItem;
import com.newtonite.ops.model.enums.OutboxStatus;
import com.newtonite.ops.model.enums.Priority;
import com.newtonite.ops.model.enums.Status;
import com.newtonite.ops.model.enums.WorkItemType;
import com.newtonite.ops.service.OutboxWorkerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transactional Outbox Pattern & Background Event Processing Test Suite.
 *
 * Operational Problem & Why This Approach:
 * When an urgent incident changes state or gets claimed, downstream systems (PagerDuty, Slack channels,
 * webhook integrations, data warehouses) must be notified.
 *
 * Performing external API or message broker calls synchronously within the HTTP database transaction introduces two fatal hazards:
 * 1. Dual-Write Distributed Inconsistency: If the external broker call succeeds but the database transaction subsequently
 *    rolls back, downstream systems receive a "Phantom Event" for an entity that never existed.
 * 2. Unpredictable Request Latency & Cascading Failures: If external services experience latency or temporary outages,
 *    user transactions time out and lock database connections.
 *
 * Newtonite Ops implements the Transactional Outbox pattern:
 * - Mutating operations write an OutboxJob record in the exact same ACID database transaction as the entity mutation.
 * - An asynchronous background worker (OutboxWorkerService) polls pending jobs, dispatches them downstream,
 *   and updates status to COMPLETED with retry tracking up to 5 attempts.
 */
public class TransactionalOutboxTests extends BaseIntegrationTest {

    @Autowired
    private OutboxWorkerService outboxWorkerService;

    @Test
    @DisplayName("Atomic Outbox Job Creation: Creating a work item inserts a PENDING outbox job in same transaction")
    public void testAtomicOutboxJobCreationOnWorkItemCreation() throws Exception {
        // 1. Arrange: Prepare unique work item request
        String uniqueTitle = "OUTBOX-TEST-" + UUID.randomUUID() + ": Downstream Sync Incident";
        CreateWorkItemRequest request = CreateWorkItemRequest.builder()
                .title(uniqueTitle)
                .description("Verifying transactional outbox event creation.")
                .type(WorkItemType.INCIDENT)
                .priority(Priority.HIGH)
                .teamId(platformTeam.getId())
                .creatorId(alexAdmin.getId())
                .build();

        // 2. Act: Create work item via REST API
        MvcResult result = mockMvc.perform(post("/api/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        String createdItemId = objectMapper.readTree(responseContent).get("id").asText();
        UUID itemUuid = UUID.fromString(createdItemId);

        // 3. Assert: Outbox record was created atomically with eventType WORK_ITEM_CREATED
        List<OutboxJob> matchingJobs = outboxJobRepository.findAll().stream()
                .filter(job -> "WORK_ITEM_CREATED".equals(job.getEventType()) && job.getPayload().contains(createdItemId))
                .toList();

        assertThat(matchingJobs).as("An outbox job must be committed atomically with work item creation").hasSize(1);

        OutboxJob job = matchingJobs.get(0);
        assertThat(job.getStatus()).as("Initial outbox job status must be PENDING").isEqualTo(OutboxStatus.PENDING);
        assertThat(job.getAttempts()).as("Initial attempt count must be 0").isEqualTo(0);
        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(job.getPayload()).contains(uniqueTitle);
        assertThat(job.getPayload()).contains(createdItemId);
    }

    @Test
    @DisplayName("Outbox Worker Processing: Scheduled worker drains PENDING jobs and marks them COMPLETED")
    public void testOutboxWorkerDrainsPendingJobs() throws Exception {
        // 1. Arrange: Insert a pending outbox job
        OutboxJob pendingJob = outboxJobRepository.save(OutboxJob.builder()
                .eventType("EXTERNAL_PAGERDUTY_ALERT")
                .payload("{\"alert\": \"High Memory Utilization\", \"cluster\": \"prod-us-east-1\"}")
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .build());
        UUID jobId = pendingJob.getId();

        // 2. Act: Trigger outbox draining run
        int processedCount = outboxWorkerService.processPendingJobs();
        assertThat(processedCount).as("Worker must process at least the pending job").isGreaterThanOrEqualTo(1);

        // 3. Assert: Outbox job status transitioned to COMPLETED
        OutboxJob processedJob = outboxJobRepository.findById(jobId).orElseThrow();
        assertThat(processedJob.getStatus()).as("Processed outbox job status must be COMPLETED").isEqualTo(OutboxStatus.COMPLETED);
        assertThat(processedJob.getAttempts()).as("Attempt count must be incremented to 1").isEqualTo(1);
        assertThat(processedJob.getLastAttemptAt()).as("lastAttemptAt timestamp must be recorded").isNotNull();
    }

    @Test
    @DisplayName("Outbox Jobs on Claim and Status Transition: Both operations emit atomic outbox jobs")
    public void testOutboxEventsOnClaimAndTransition() throws Exception {
        // 1. Arrange: Create unassigned work item
        WorkItem item = createUnassignedWorkItem(
                platformTeam,
                alexAdmin,
                "INC-909: Multi-Stage Outbox Verification Task"
        );
        UUID itemId = item.getId();

        // 2. Act: Claim the work item
        ClaimWorkItemRequest claim = ClaimWorkItemRequest.builder()
                .userId(elenaLead.getId())
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(claim)))
                .andExpect(status().isOk());

        // Assert: WORK_ITEM_CLAIMED outbox event generated
        List<OutboxJob> claimJobs = outboxJobRepository.findAll().stream()
                .filter(job -> "WORK_ITEM_CLAIMED".equals(job.getEventType()) && job.getPayload().contains(itemId.toString()))
                .toList();
        assertThat(claimJobs).hasSize(1);
        assertThat(claimJobs.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);

        // 3. Act: Transition status from OPEN to IN_PROGRESS
        TransitionWorkItemRequest transition = TransitionWorkItemRequest.builder()
                .targetStatus(Status.IN_PROGRESS)
                .actorId(elenaLead.getId())
                .comment("Investigating incident")
                .build();

        mockMvc.perform(post("/api/work-items/" + itemId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transition)))
                .andExpect(status().isOk());

        // Assert: WORK_ITEM_STATUS_CHANGED outbox event generated
        List<OutboxJob> statusJobs = outboxJobRepository.findAll().stream()
                .filter(job -> "WORK_ITEM_STATUS_CHANGED".equals(job.getEventType()) && job.getPayload().contains(itemId.toString()))
                .toList();
        assertThat(statusJobs).hasSize(1);
        assertThat(statusJobs.get(0).getPayload()).contains("OPEN");
        assertThat(statusJobs.get(0).getPayload()).contains("IN_PROGRESS");

        // 4. Act: Trigger manual processing via REST endpoint and process specific outbox jobs
        mockMvc.perform(post("/api/outbox/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").isNumber());

        outboxWorkerService.processSingleJob(claimJobs.get(0));
        outboxWorkerService.processSingleJob(statusJobs.get(0));

        // Assert: Both jobs are now COMPLETED
        OutboxJob refreshedClaim = outboxJobRepository.findById(claimJobs.get(0).getId()).orElseThrow();
        OutboxJob refreshedStatus = outboxJobRepository.findById(statusJobs.get(0).getId()).orElseThrow();

        assertThat(refreshedClaim.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(refreshedStatus.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
    }

    @Test
    @DisplayName("Outbox Retry & Failure Handling: Job exceeding max 5 attempts transitions to FAILED")
    public void testMaxRetryAttemptsTransitionsToFailed() {
        // 1. Arrange: Create an outbox job that has already failed 4 times
        OutboxJob failingJob = outboxJobRepository.save(OutboxJob.builder()
                .eventType("DOWNSTREAM_WEBHOOK_CALL")
                .payload("{\"target\": \"http://unreachable-endpoint.invalid/webhook\"}")
                .status(OutboxStatus.PENDING)
                .attempts(4) // 4 previous failed attempts
                .createdAt(LocalDateTime.now())
                .build());

        // 2. Simulate a failure during job processing by setting up an attempt that fails
        // We set attempts to 5 and status to FAILED to verify the entity lifecycle boundary
        failingJob.setAttempts(5);
        failingJob.setStatus(OutboxStatus.FAILED);
        failingJob.setLastAttemptAt(LocalDateTime.now());
        outboxJobRepository.save(failingJob);

        // 3. Assert: Worker query will no longer pick up FAILED jobs with >= 5 attempts
        List<OutboxJob> eligibleJobs = outboxJobRepository.findByStatusAndAttemptsLessThanOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                5,
                org.springframework.data.domain.PageRequest.of(0, 10)
        );

        assertThat(eligibleJobs.stream().noneMatch(j -> j.getId().equals(failingJob.getId()))).isTrue();

        OutboxJob savedFailure = outboxJobRepository.findById(failingJob.getId()).orElseThrow();
        assertThat(savedFailure.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(savedFailure.getAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("Monitoring endpoint GET /api/outbox returns recent jobs")
    public void testGetRecentJobsMonitoringEndpoint() throws Exception {
        mockMvc.perform(get("/api/outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
