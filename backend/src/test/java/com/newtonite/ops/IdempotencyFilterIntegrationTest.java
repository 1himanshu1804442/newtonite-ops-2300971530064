package com.newtonite.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newtonite.ops.dto.CreateWorkItemRequest;
import com.newtonite.ops.model.entity.Team;
import com.newtonite.ops.model.entity.User;
import com.newtonite.ops.model.enums.Priority;
import com.newtonite.ops.model.enums.WorkItemType;
import com.newtonite.ops.repository.TeamRepository;
import com.newtonite.ops.repository.UserRepository;
import com.newtonite.ops.repository.WorkItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class IdempotencyFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private WorkItemRepository workItemRepository;

    @Test
    @DisplayName("Idempotency Filter: Replay of POST with identical Idempotency-Key returns cached response with HIT header")
    void testIdempotencyReplay() throws Exception {
        Team team = teamRepository.findByCode("ENG-PLAT").orElseThrow();
        User creator = userRepository.findByEmail("alex.rivera@newtonite.io").orElseThrow();

        CreateWorkItemRequest payload = CreateWorkItemRequest.builder()
                .title("Idempotency verification item")
                .description("Ensures replay protection works")
                .type(WorkItemType.FEATURE)
                .priority(Priority.MEDIUM)
                .teamId(team.getId())
                .creatorId(creator.getId())
                .build();

        String idempotencyKey = "key-" + UUID.randomUUID();
        long initialCount = workItemRepository.count();

        // 1. Initial Request (MISS)
        MvcResult firstResult = mockMvc.perform(post("/api/work-items")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Cache-Lookup", "MISS"))
                .andExpect(header().string("Idempotency-Key", idempotencyKey))
                .andReturn();

        assertEquals(initialCount + 1, workItemRepository.count(), "One new work item should be created on first request");

        // 2. Replay Request with identical Idempotency-Key (HIT)
        MvcResult replayResult = mockMvc.perform(post("/api/work-items")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Cache-Lookup", "HIT"))
                .andExpect(header().string("Idempotency-Key", idempotencyKey))
                .andReturn();

        // Database count must NOT increase
        assertEquals(initialCount + 1, workItemRepository.count(), "Replay must NOT create an additional entity");

        // Response payloads must match
        assertEquals(firstResult.getResponse().getContentAsString().trim(),
                replayResult.getResponse().getContentAsString().trim());
    }
}
