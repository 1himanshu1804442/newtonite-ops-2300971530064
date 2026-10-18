package com.newtonite.ops.dto;

import com.newtonite.ops.model.entity.WorkItem;
import com.newtonite.ops.model.enums.Priority;
import com.newtonite.ops.model.enums.Status;
import com.newtonite.ops.model.enums.WorkItemType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representation of a WorkItem returned by API endpoints.
 * Includes nested team, creator, assignee, and JPA version for optimistic locking.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemResponse {

    private UUID id;
    private String title;
    private String description;
    private WorkItemType type;
    private Priority priority;
    private Status status;
    private TeamResponse team;
    private UserResponse creator;
    private UserResponse assignee;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WorkItemResponse fromEntity(WorkItem item) {
        if (item == null) {
            return null;
        }
        return WorkItemResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .type(item.getType())
                .priority(item.getPriority())
                .status(item.getStatus())
                .team(TeamResponse.fromEntity(item.getTeam()))
                .creator(UserResponse.fromEntity(item.getCreator()))
                .assignee(UserResponse.fromEntity(item.getAssignee()))
                .version(item.getVersion())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
