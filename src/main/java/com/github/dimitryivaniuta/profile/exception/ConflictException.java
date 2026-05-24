package com.github.dimitryivaniuta.profile.exception;

/**
 * Raised when a request conflicts with current persisted state.
 */
public class ConflictException extends RuntimeException {

    /**
     * Creates a conflict exception.
     *
     * @param message safe error message
     */
    public ConflictException(String message) {
        super(message);
    }
}
