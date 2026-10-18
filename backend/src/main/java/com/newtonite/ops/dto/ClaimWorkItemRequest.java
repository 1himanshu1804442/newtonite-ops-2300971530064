package com.newtonite.ops.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * Payload for claiming an unassigned work item concurrently.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimWorkItemRequest {

    @NotNull(message = "User ID claiming the item is required")
    private UUID userId;
}
