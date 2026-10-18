package com.newtonite.ops.model.enums;

/**
 * Audit event action types for tracking every operational mutation.
 */
public enum ActionType {
    CREATED,
    UPDATED,
    STATUS_CHANGED,
    ASSIGNED,
    UNASSIGNED,
    CLAIMED,
    COMMENTED
}
