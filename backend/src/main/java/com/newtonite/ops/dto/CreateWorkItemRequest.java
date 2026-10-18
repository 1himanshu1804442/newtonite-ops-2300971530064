package com.newtonite.ops.dto;

import com.newtonite.ops.model.enums.Priority;
import com.newtonite.ops.model.enums.WorkItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * Payload for creating a new work item.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWorkItemRequest {

    @NotBlank(message = "Title is required and cannot be blank")
    private String title;

    private String description;

    @NotNull(message = "Work item type is required")
    private WorkItemType type;

    @NotNull(message = "Priority is required")
    private Priority priority;

    @NotNull(message = "Team ID is required")
    private UUID teamId;

    @NotNull(message = "Creator ID is required")
    private UUID creatorId;

    private UUID assigneeId;
}
