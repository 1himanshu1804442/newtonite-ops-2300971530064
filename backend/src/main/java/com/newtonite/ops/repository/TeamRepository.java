package com.newtonite.ops.repository;

import com.newtonite.ops.model.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Data access repository for Team entities.
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    /**
     * Finds a team by its unique operational team code (e.g. SRE-CORE).
     */
    Optional<Team> findByCode(String code);
}
