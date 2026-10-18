package com.newtonite.ops.model.enums;

/**
 * Status stages representing the formal operational lifecycle of a work item.
 * Linear progression: OPEN -> IN_PROGRESS -> AWAITING_APPROVAL -> RESOLVED -> CLOSED.
 */
public enum Status {
    OPEN,
    IN_PROGRESS,
    AWAITING_APPROVAL,
    RESOLVED,
    CLOSED
}
