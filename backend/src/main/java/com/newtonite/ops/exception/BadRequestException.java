package com.newtonite.ops.exception;

/**
 * Thrown when incoming request parameters or business payloads violate contract constraints.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
