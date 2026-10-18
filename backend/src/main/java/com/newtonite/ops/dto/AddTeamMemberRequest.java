package com.newtonite.ops.dto;

import com.newtonite.ops.model.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * Payload for adding or updating a team member role.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddTeamMemberRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Role is required (VIEWER, MEMBER, LEAD, ADMIN)")
    private Role role;
}
