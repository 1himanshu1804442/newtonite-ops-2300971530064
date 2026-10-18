package com.newtonite.ops.controller;

import com.newtonite.ops.dto.AddTeamMemberRequest;
import com.newtonite.ops.dto.CreateTeamRequest;
import com.newtonite.ops.dto.TeamMembershipResponse;
import com.newtonite.ops.dto.TeamResponse;
import com.newtonite.ops.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for teams and team membership management.
 */
@Slf4j
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    /**
     * Lists all operational teams.
     */
    @GetMapping
    public ResponseEntity<List<TeamResponse>> listTeams() {
        log.info("REST: GET /api/teams");
        return ResponseEntity.ok(teamService.getAllTeams());
    }

    /**
     * Retrieves details for a specific team.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeam(@PathVariable UUID id) {
        log.info("REST: GET /api/teams/{}", id);
        return ResponseEntity.ok(teamService.getTeamById(id));
    }

    /**
     * Creates a new team.
     */
    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        log.info("REST: POST /api/teams code={}", request.getCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.createTeam(request));
    }

    /**
     * Retrieves members and assigned roles for a team.
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<List<TeamMembershipResponse>> getTeamMembers(@PathVariable UUID id) {
        log.info("REST: GET /api/teams/{}/members", id);
        return ResponseEntity.ok(teamService.getTeamMembers(id));
    }

    /**
     * Adds or updates a team member's role.
     */
    @PostMapping("/{id}/members")
    public ResponseEntity<TeamMembershipResponse> addOrUpdateMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddTeamMemberRequest request
    ) {
        log.info("REST: POST /api/teams/{}/members userId={}, role={}", id, request.getUserId(), request.getRole());
        return ResponseEntity.ok(teamService.addOrUpdateMember(id, request));
    }
}
