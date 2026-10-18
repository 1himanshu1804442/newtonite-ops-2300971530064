package com.newtonite.ops.exception;

/**
 * Thrown when a requested resource (User, Team, WorkItem) cannot be found by its identifier.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
