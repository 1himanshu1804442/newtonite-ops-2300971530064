package com.newtonite.ops.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * Payload for explicitly assigning a work item to an operator.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignWorkItemRequest {

    @NotNull(message = "Assignee ID is required")
    private UUID assigneeId;

    @NotNull(message = "Actor ID is required for audit trail tracking")
    private UUID actorId;

    private Long expectedVersion;
}
