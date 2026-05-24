package com.github.dimitryivaniuta.profile.exception;

/**
 * Raised when a requested profile or resource is not available.
 */
public class NotFoundException extends RuntimeException {

    /**
     * Creates a not-found exception.
     *
     * @param message safe error message
     */
    public NotFoundException(String message) {
        super(message);
    }
}
