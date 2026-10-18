package com.newtonite.ops.exception;

/**
 * Thrown when an operation encounters a state conflict (HTTP 409), such as
 * attempting to claim an already assigned work item, or version divergence during optimistic locking.
 */
public class ConflictException extends RuntimeException {

    private final String errorCode;
    private final Object details;

    public ConflictException(String message) {
        super(message);
        this.errorCode = "STATE_CONFLICT";
        this.details = null;
    }

    public ConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    public ConflictException(String errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}
