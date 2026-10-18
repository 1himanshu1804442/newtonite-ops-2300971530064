package com.newtonite.ops.exception;

/**
 * Thrown when an illegal workflow state transition is attempted
 * (e.g., jumping straight from OPEN to CLOSED, skipping AWAITING_APPROVAL).
 */
public class InvalidWorkflowTransitionException extends RuntimeException {

    public InvalidWorkflowTransitionException(String message) {
        super(message);
    }
}
