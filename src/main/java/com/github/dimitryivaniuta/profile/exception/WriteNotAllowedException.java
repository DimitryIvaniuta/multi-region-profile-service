package com.github.dimitryivaniuta.profile.exception;

/**
 * Raised when a non-primary region receives a write request.
 */
public class WriteNotAllowedException extends RuntimeException {

    /**
     * Creates a write-not-allowed exception.
     *
     * @param message safe error message
     */
    public WriteNotAllowedException(String message) {
        super(message);
    }
}
