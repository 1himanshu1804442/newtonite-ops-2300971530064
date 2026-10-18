package com.newtonite.ops.model.enums;

/**
 * Processing states for transactional outbox jobs.
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
