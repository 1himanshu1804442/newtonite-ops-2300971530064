package com.newtonite.ops.dto;

import com.newtonite.ops.model.entity.TeamMembership;
import com.newtonite.ops.model.enums.Role;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representation of a TeamMembership relationship returned by API endpoints.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMembershipResponse {

    private UUID id;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String userAvatarUrl;
    private UUID teamId;
    private String teamName;
    private String teamCode;
    private Role role;
    private LocalDateTime createdAt;

    public static TeamMembershipResponse fromEntity(TeamMembership membership) {
        if (membership == null) {
            return null;
        }
        return TeamMembershipResponse.builder()
                .id(membership.getId())
                .userId(membership.getUser().getId())
                .userName(membership.getUser().getName())
                .userEmail(membership.getUser().getEmail())
                .userAvatarUrl(membership.getUser().getAvatarUrl())
                .teamId(membership.getTeam().getId())
                .teamName(membership.getTeam().getName())
                .teamCode(membership.getTeam().getCode())
                .role(membership.getRole())
                .createdAt(membership.getCreatedAt())
                .build();
    }
}
