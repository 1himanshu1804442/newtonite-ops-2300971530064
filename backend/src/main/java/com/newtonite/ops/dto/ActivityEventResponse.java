package com.newtonite.ops.dto;

import com.newtonite.ops.model.entity.ActivityEvent;
import com.newtonite.ops.model.enums.ActionType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representation of an ActivityEvent audit item returned by API endpoints.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityEventResponse {

    private UUID id;
    private UUID workItemId;
    private UserResponse actor;
    private ActionType actionType;
    private String description;
    private String previousState;
    private String newState;
    private LocalDateTime createdAt;

    public static ActivityEventResponse fromEntity(ActivityEvent event) {
        if (event == null) {
            return null;
        }
        return ActivityEventResponse.builder()
                .id(event.getId())
                .workItemId(event.getWorkItem().getId())
                .actor(UserResponse.fromEntity(event.getActor()))
                .actionType(event.getActionType())
                .description(event.getDescription())
                .previousState(event.getPreviousState())
                .newState(event.getNewState())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
