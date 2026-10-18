package com.newtonite.ops.exception;

/**
 * Thrown when an operation encounters a state conflict (HTTP 409), such as
 * attempting to claim an already assigned work item, or version divergence during optimistic locking.
 */
public class ConflictException extends RuntimeException {

    private final String errorCode;

    public ConflictException(String message) {
        super(message);
        this.errorCode = "STATE_CONFLICT";
    }

    public ConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
