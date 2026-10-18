package com.newtonite.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Payload for registering a new engineering/operations team.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTeamRequest {

    @NotBlank(message = "Team name cannot be blank")
    private String name;

    @NotBlank(message = "Team code cannot be blank (e.g. SRE-CORE)")
    private String code;

    private String description;
}
