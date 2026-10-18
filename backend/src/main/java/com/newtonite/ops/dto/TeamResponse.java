package com.newtonite.ops.dto;

import com.newtonite.ops.model.entity.Team;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representation of a Team returned by API endpoints.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamResponse {

    private UUID id;
    private String name;
    private String code;
    private String description;
    private LocalDateTime createdAt;
    private int memberCount;

    public static TeamResponse fromEntity(Team team) {
        if (team == null) {
            return null;
        }
        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .code(team.getCode())
                .description(team.getDescription())
                .createdAt(team.getCreatedAt())
                .build();
    }
}
