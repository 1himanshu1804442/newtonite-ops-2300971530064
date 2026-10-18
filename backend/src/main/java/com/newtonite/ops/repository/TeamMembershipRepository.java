package com.newtonite.ops.repository;

import com.newtonite.ops.model.entity.TeamMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access repository for TeamMembership relationships and role checks.
 */
@Repository
public interface TeamMembershipRepository extends JpaRepository<TeamMembership, UUID> {

    /**
     * Finds a membership record by user ID and team ID.
     */
    Optional<TeamMembership> findByUserIdAndTeamId(UUID userId, UUID teamId);

    /**
     * Finds all members associated with a given team.
     */
    List<TeamMembership> findByTeamId(UUID teamId);

    /**
     * Finds all team memberships held by a given user.
     */
    List<TeamMembership> findByUserId(UUID userId);
}
