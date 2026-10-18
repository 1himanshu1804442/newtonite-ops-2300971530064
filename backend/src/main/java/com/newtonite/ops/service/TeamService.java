package com.newtonite.ops.service;

import com.newtonite.ops.dto.AddTeamMemberRequest;
import com.newtonite.ops.dto.CreateTeamRequest;
import com.newtonite.ops.dto.TeamMembershipResponse;
import com.newtonite.ops.dto.TeamResponse;
import com.newtonite.ops.exception.ConflictException;
import com.newtonite.ops.exception.ResourceNotFoundException;
import com.newtonite.ops.model.entity.Team;
import com.newtonite.ops.model.entity.TeamMembership;
import com.newtonite.ops.model.entity.User;
import com.newtonite.ops.model.enums.Role;
import com.newtonite.ops.repository.TeamMembershipRepository;
import com.newtonite.ops.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing engineering teams and team membership roles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final UserService userService;

    /**
     * Lists all teams with their active member counts.
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> getAllTeams() {
        log.info("Fetching all registered teams");
        return teamRepository.findAll().stream()
                .map(team -> {
                    TeamResponse res = TeamResponse.fromEntity(team);
                    res.setMemberCount(teamMembershipRepository.findByTeamId(team.getId()).size());
                    return res;
                })
                .toList();
    }

    /**
     * Retrieves team details by ID.
     */
    @Transactional(readOnly = true)
    public TeamResponse getTeamById(UUID id) {
        log.info("Fetching team details for id: {}", id);
        Team team = getTeamEntity(id);
        TeamResponse res = TeamResponse.fromEntity(team);
        res.setMemberCount(teamMembershipRepository.findByTeamId(team.getId()).size());
        return res;
    }

    /**
     * Internal lookup returning the managed JPA Team entity.
     */
    @Transactional(readOnly = true)
    public Team getTeamEntity(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + id));
    }

    /**
     * Registers a new operational team.
     */
    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request) {
        log.info("Creating new team: code={}, name={}", request.getCode(), request.getName());

        teamRepository.findByCode(request.getCode()).ifPresent(existing -> {
            throw new ConflictException("DUPLICATE_TEAM_CODE", "A team with code '" + request.getCode() + "' already exists.");
        });

        Team team = Team.builder()
                .name(request.getName())
                .code(request.getCode().trim().toUpperCase())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .build();

        Team saved = teamRepository.save(team);
        return TeamResponse.fromEntity(saved);
    }

    /**
     * Retrieves all memberships and roles for a specific team.
     */
    @Transactional(readOnly = true)
    public List<TeamMembershipResponse> getTeamMembers(UUID teamId) {
        log.info("Fetching team members for teamId: {}", teamId);
        // Verify team exists
        getTeamEntity(teamId);
        return teamMembershipRepository.findByTeamId(teamId).stream()
                .map(TeamMembershipResponse::fromEntity)
                .toList();
    }

    /**
     * Adds a user to a team or updates their existing role.
     */
    @Transactional
    public TeamMembershipResponse addOrUpdateMember(UUID teamId, AddTeamMemberRequest request) {
        log.info("Updating membership: teamId={}, userId={}, role={}", teamId, request.getUserId(), request.getRole());
        Team team = getTeamEntity(teamId);
        User user = userService.getUserEntity(request.getUserId());

        Optional<TeamMembership> existingOpt = teamMembershipRepository.findByUserIdAndTeamId(user.getId(), team.getId());
        TeamMembership membership;
        if (existingOpt.isPresent()) {
            membership = existingOpt.get();
            membership.setRole(request.getRole());
        } else {
            membership = TeamMembership.builder()
                    .team(team)
                    .user(user)
                    .role(request.getRole())
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        TeamMembership saved = teamMembershipRepository.save(membership);
        return TeamMembershipResponse.fromEntity(saved);
    }

    /**
     * Checks if a user possesses LEAD or ADMIN privileges within a team.
     */
    @Transactional(readOnly = true)
    public boolean isUserLeadOrAdmin(UUID userId, UUID teamId) {
        Optional<TeamMembership> membership = teamMembershipRepository.findByUserIdAndTeamId(userId, teamId);
        return membership.map(m -> m.getRole() == Role.LEAD || m.getRole() == Role.ADMIN).orElse(false);
    }

    /**
     * Checks if a user is an enrolled member of a team with any role.
     */
    @Transactional(readOnly = true)
    public boolean isUserMemberOfTeam(UUID userId, UUID teamId) {
        return teamMembershipRepository.findByUserIdAndTeamId(userId, teamId).isPresent();
    }
}
