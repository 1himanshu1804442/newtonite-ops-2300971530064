package com.newtonite.ops.dto;

import com.newtonite.ops.model.enums.Priority;
import com.newtonite.ops.model.enums.WorkItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * Payload for updating an existing work item.
 * Includes expectedVersion to enforce optimistic concurrency control before persistence.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWorkItemRequest {

    @NotBlank(message = "Title is required and cannot be blank")
    private String title;

    private String description;

    @NotNull(message = "Work item type is required")
    private WorkItemType type;

    @NotNull(message = "Priority is required")
    private Priority priority;

    /**
     * Expected entity version for optimistic concurrency checks.
     * Prevents blind overwrite if another user modified the item in the meantime.
     */
    @NotNull(message = "Expected version is required for optimistic concurrency verification")
    private Long expectedVersion;

    @NotNull(message = "Actor ID is required for audit trail tracking")
    private UUID actorId;
}
