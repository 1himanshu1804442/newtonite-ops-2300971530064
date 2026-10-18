package com.newtonite.ops.model.entity;

import com.newtonite.ops.model.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional Outbox pattern entity.
 * Jobs are committed atomically with business transactions and processed reliably
 * by asynchronous background worker polling.
 */
@Entity
@Table(name = "outbox_jobs", indexes = {
    @Index(name = "idx_outbox_jobs_status_attempts", columnList = "status, attempts")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.attempts == null) {
            this.attempts = 0;
        }
        if (this.status == null) {
            this.status = OutboxStatus.PENDING;
        }
    }
}
