package com.newtonite.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newtonite.ops.model.entity.*;
import com.newtonite.ops.model.enums.Priority;
import com.newtonite.ops.model.enums.Role;
import com.newtonite.ops.model.enums.Status;
import com.newtonite.ops.model.enums.WorkItemType;
import com.newtonite.ops.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base integration test providing MockMvc, repositories, JSON serialization,
 * and deterministic seeded personas (Teams, Users, Memberships) for all test suites.
 *
 * Configured with the 'dev' profile to use in-memory PostgreSQL compatibility mode.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected WorkItemRepository workItemRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TeamRepository teamRepository;

    @Autowired
    protected TeamMembershipRepository teamMembershipRepository;

    @Autowired
    protected ActivityEventRepository activityEventRepository;

    @Autowired
    protected OutboxJobRepository outboxJobRepository;

    @Autowired
    protected IdempotencyRecordRepository idempotencyRecordRepository;

    // Deterministic Seeded Test Personas
    protected Team platformTeam;
    protected Team sreTeam;

    protected User alexAdmin;      // Platform Team Admin
    protected User elenaLead;       // Platform Team Lead
    protected User marcusMember;    // Platform Team Member

    protected User sarahAdmin;      // SRE Team Admin
    protected User vikramLead;      // SRE Team Lead
    protected User chloeMember;     // SRE Team Member (Outside Platform Team)

    @BeforeEach
    public void setupSeededEntities() {
        // Seed Platform Engineering Team
        platformTeam = teamRepository.findByCode("ENG-PLAT").orElseGet(() ->
                teamRepository.save(Team.builder()
                        .name("Platform Engineering")
                        .code("ENG-PLAT")
                        .description("Core infrastructure and developer tools")
                        .createdAt(LocalDateTime.now())
                        .build())
        );

        // Seed SRE Team
        sreTeam = teamRepository.findByCode("SRE-CORE").orElseGet(() ->
                teamRepository.save(Team.builder()
                        .name("Site Reliability Engineering")
                        .code("SRE-CORE")
                        .description("Reliability and incident response")
                        .createdAt(LocalDateTime.now())
                        .build())
        );

        // Seed Users for Platform Team
        alexAdmin = getOrCreateUser("alex.admin@newtonite.io", "Alex Rivera");
        elenaLead = getOrCreateUser("elena.lead@newtonite.io", "Elena Rostova");
        marcusMember = getOrCreateUser("marcus.member@newtonite.io", "Marcus Chen");

        // Seed Users for SRE Team
        sarahAdmin = getOrCreateUser("sarah.admin@newtonite.io", "Sarah Connor");
        vikramLead = getOrCreateUser("vikram.lead@newtonite.io", "Vikram Patel");
        chloeMember = getOrCreateUser("chloe.member@newtonite.io", "Chloe Martin");

        // Assign Memberships for Platform Team
        ensureMembership(alexAdmin, platformTeam, Role.ADMIN);
        ensureMembership(elenaLead, platformTeam, Role.LEAD);
        ensureMembership(marcusMember, platformTeam, Role.MEMBER);

        // Assign Memberships for SRE Team
        ensureMembership(sarahAdmin, sreTeam, Role.ADMIN);
        ensureMembership(vikramLead, sreTeam, Role.LEAD);
        ensureMembership(chloeMember, sreTeam, Role.MEMBER);
    }

    private User getOrCreateUser(String email, String name) {
        return userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(User.builder()
                        .name(name)
                        .email(email)
                        .avatarUrl("https://images.unsplash.com/photo-example")
                        .createdAt(LocalDateTime.now())
                        .build())
        );
    }

    private void ensureMembership(User user, Team team, Role role) {
        teamMembershipRepository.findByUserIdAndTeamId(user.getId(), team.getId())
                .ifPresentOrElse(
                        m -> {
                            if (m.getRole() != role) {
                                m.setRole(role);
                                teamMembershipRepository.save(m);
                            }
                        },
                        () -> teamMembershipRepository.save(TeamMembership.builder()
                                .user(user)
                                .team(team)
                                .role(role)
                                .createdAt(LocalDateTime.now())
                                .build())
                );
    }

    /**
     * Helper to create and persist an unassigned work item in a team.
     */
    protected WorkItem createUnassignedWorkItem(Team team, User creator, String title) {
        WorkItem item = WorkItem.builder()
                .title(title)
                .description("Automated test description for " + title)
                .type(WorkItemType.INCIDENT)
                .priority(Priority.HIGH)
                .status(Status.OPEN)
                .team(team)
                .creator(creator)
                .assignee(null) // Unassigned for claiming tests
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return workItemRepository.saveAndFlush(item);
    }

    /**
     * Helper to create and persist an assigned work item in a team.
     */
    protected WorkItem createAssignedWorkItem(Team team, User creator, User assignee, Status status, String title) {
        WorkItem item = WorkItem.builder()
                .title(title)
                .description("Automated test description for " + title)
                .type(WorkItemType.BUG)
                .priority(Priority.MEDIUM)
                .status(status)
                .team(team)
                .creator(creator)
                .assignee(assignee)
                .version(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return workItemRepository.saveAndFlush(item);
    }
}
