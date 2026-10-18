package com.newtonite.ops.exception;

/**
 * Thrown when an actor does not possess sufficient privileges
 * (e.g., non-Lead/Admin user attempting to approve or resolve a work item).
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
