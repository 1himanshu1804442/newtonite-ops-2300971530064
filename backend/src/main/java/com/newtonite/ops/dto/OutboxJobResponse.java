package com.newtonite.ops.dto;

import com.newtonite.ops.model.entity.OutboxJob;
import com.newtonite.ops.model.enums.OutboxStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representation of an OutboxJob returned by API endpoints.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxJobResponse {

    private UUID id;
    private String eventType;
    private String payload;
    private OutboxStatus status;
    private Integer attempts;
    private LocalDateTime createdAt;
    private LocalDateTime lastAttemptAt;

    public static OutboxJobResponse fromEntity(OutboxJob job) {
        if (job == null) {
            return null;
        }
        return OutboxJobResponse.builder()
                .id(job.getId())
                .eventType(job.getEventType())
                .payload(job.getPayload())
                .status(job.getStatus())
                .attempts(job.getAttempts())
                .createdAt(job.getCreatedAt())
                .lastAttemptAt(job.getLastAttemptAt())
                .build();
    }
}
