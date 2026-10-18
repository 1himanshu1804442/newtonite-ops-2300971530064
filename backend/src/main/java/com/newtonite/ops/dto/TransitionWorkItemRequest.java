package com.newtonite.ops.dto;

import com.newtonite.ops.model.enums.Status;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * Payload for triggering a state machine status transition on a work item.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitionWorkItemRequest {

    @NotNull(message = "Target status is required")
    private Status targetStatus;

    @NotNull(message = "Actor ID is required for role authorization and audit logging")
    private UUID actorId;

    private String comment;

    /**
     * Optional expected version for concurrency guarding.
     */
    private Long expectedVersion;
}
