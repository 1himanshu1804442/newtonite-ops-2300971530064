package com.newtonite.ops;

import com.newtonite.ops.dto.CreateWorkItemRequest;
import com.newtonite.ops.model.entity.IdempotencyRecord;
import com.newtonite.ops.model.entity.WorkItem;
import com.newtonite.ops.model.enums.Priority;
import com.newtonite.ops.model.enums.WorkItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotency Protection for Non-Idempotent Operations Test Suite.
 *
 * Operational Problem & Why This Approach:
 * In high-pressure operational environments with flaky network connections (e.g., VPN drops, mobile hotspots,
 * or impatient operators double-clicking the "Submit Incident" button), a client can successfully dispatch
 * an HTTP POST request that commits in the database, but the client disconnects before receiving the HTTP 201 response.
 *
 * The client or browser will naturally retry the request. Under naive REST CRUD, this causes duplicate entities
 * (duplicate incidents, duplicate payments investigations, or duplicate remediation tasks) to be inserted in the DB.
 *
 * UI button disabling is insufficient because it cannot protect against network-level retries, parallel browser tabs,
 * or command-line scripts.
 *
 * Newtonite Ops adopts the enterprise IETF/Stripe standard:
 * Clients submit a unique 'Idempotency-Key' UUID header with mutating requests.
 * An IdempotencyFilter checks the idempotency_records table:
 * - On cache MISS: Executes the request, records the resulting HTTP status code and response body, and tags 'X-Cache-Lookup: MISS'.
 * - On cache HIT: Immediately serves the cached response body and status code without executing business transactions, tagging 'X-Cache-Lookup: HIT'.
 */
public class IdempotencyTests extends BaseIntegrationTest {

    @Test
    @DisplayName("Duplicate POST with same Idempotency-Key returns exact same response and creates only 1 entity in DB")
    public void testDuplicatePostWithSameIdempotencyKey() throws Exception {
        // 1. Arrange: Generate unique idempotency key and build request payload
        String idempotencyKey = UUID.randomUUID().toString();
        String uniqueTitle = "INC-" + UUID.randomUUID() + ": Network Switch Packet Drop";

        CreateWorkItemRequest request = CreateWorkItemRequest.builder()
                .title(uniqueTitle)
                .description("Switch eth0 experiencing 12% frame CRC errors under peak traffic load.")
                .type(WorkItemType.INCIDENT)
                .priority(Priority.HIGH)
                .teamId(platformTeam.getId())
                .creatorId(alexAdmin.getId())
                .build();

        long initialDbCount = workItemRepository.count();

        // 2. Act (First Request): Initial submission - expect cache MISS
        MvcResult firstResult = mockMvc.perform(post("/api/work-items")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()) // HTTP 201 Created
                .andExpect(header().string("X-Cache-Lookup", "MISS"))
                .andExpect(header().string("Idempotency-Key", idempotencyKey))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value(uniqueTitle))
                .andReturn();

        String firstResponseBody = firstResult.getResponse().getContentAsString();
        assertThat(firstResponseBody).isNotEmpty();

        // Verify database after first request: count increased by exactly 1
        long afterFirstCount = workItemRepository.count();
        assertThat(afterFirstCount).as("Database count must increase by 1 after first submission").isEqualTo(initialDbCount + 1);

        // Verify idempotency record persisted in database
        Optional<IdempotencyRecord> recordOpt = idempotencyRecordRepository.findById(idempotencyKey);
        assertThat(recordOpt).isPresent();
        assertThat(recordOpt.get().getStatusCode()).isEqualTo(201);
        assertThat(recordOpt.get().getResponseBody()).isEqualTo(firstResponseBody);

        // 3. Act (Second Request): Duplicate retry with the EXACT SAME Idempotency-Key
        MvcResult secondResult = mockMvc.perform(post("/api/work-items")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()) // HTTP 201 Created from cache
                .andExpect(header().string("X-Cache-Lookup", "HIT")) // Crucial: Cache HIT!
                .andExpect(header().string("Idempotency-Key", idempotencyKey))
                .andReturn();

        String secondResponseBody = secondResult.getResponse().getContentAsString();

        // 4. Assert: Second response body is EXACTLY IDENTICAL to the first response
        assertThat(secondResponseBody).as("Cached idempotent response body must match first response byte-for-byte").isEqualTo(firstResponseBody);

        // 5. Assert: ONLY 1 entity exists in the database
        long finalDbCount = workItemRepository.count();
        assertThat(finalDbCount).as("Database count must NOT increase on duplicate submission").isEqualTo(afterFirstCount);

        List<WorkItem> matchingItems = workItemRepository.findAll().stream()
                .filter(item -> item.getTitle().equals(uniqueTitle))
                .toList();
        assertThat(matchingItems).as("Exactly 1 work item with this title must exist in DB").hasSize(1);
    }

    @Test
    @DisplayName("Distinct Idempotency-Keys create distinct entities in DB")
    public void testDistinctIdempotencyKeysCreateDistinctEntities() throws Exception {
        // Arrange
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        CreateWorkItemRequest request1 = CreateWorkItemRequest.builder()
                .title("TASK-1001: Distinct Key 1 Task")
                .description("Task description for key 1")
                .type(WorkItemType.TASK)
                .priority(Priority.LOW)
                .teamId(platformTeam.getId())
                .creatorId(marcusMember.getId())
                .build();

        CreateWorkItemRequest request2 = CreateWorkItemRequest.builder()
                .title("TASK-1002: Distinct Key 2 Task")
                .description("Task description for key 2")
                .type(WorkItemType.TASK)
                .priority(Priority.LOW)
                .teamId(platformTeam.getId())
                .creatorId(marcusMember.getId())
                .build();

        // First request with key1 -> MISS -> 201
        mockMvc.perform(post("/api/work-items")
                        .header("Idempotency-Key", key1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Cache-Lookup", "MISS"));

        // Second request with key2 -> MISS -> 201
        mockMvc.perform(post("/api/work-items")
                        .header("Idempotency-Key", key2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Cache-Lookup", "MISS"));

        // Both idempotency records exist in DB
        assertThat(idempotencyRecordRepository.findById(key1)).isPresent();
        assertThat(idempotencyRecordRepository.findById(key2)).isPresent();
    }

    @Test
    @DisplayName("Duplicate request with validation error caches and returns the same 400 Bad Request")
    public void testIdempotencyOnValidationError() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        // Malformed request with missing title (violates @NotBlank)
        CreateWorkItemRequest invalidRequest = CreateWorkItemRequest.builder()
                .title("") // Blank title
                .type(WorkItemType.BUG)
                .priority(Priority.MEDIUM)
                .teamId(platformTeam.getId())
                .creatorId(alexAdmin.getId())
                .build();

        // First attempt returns 400 Bad Request
        MvcResult firstResult = mockMvc.perform(post("/api/work-items")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Cache-Lookup", "MISS"))
                .andReturn();

        String firstErrorBody = firstResult.getResponse().getContentAsString();

        // Second attempt with same key returns cached 400 Bad Request
        MvcResult secondResult = mockMvc.perform(post("/api/work-items")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Cache-Lookup", "HIT"))
                .andReturn();

        String secondErrorBody = secondResult.getResponse().getContentAsString();
        assertThat(secondErrorBody).isEqualTo(firstErrorBody);
    }
}
