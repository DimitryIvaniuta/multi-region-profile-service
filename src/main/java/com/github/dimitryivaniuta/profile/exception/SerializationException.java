package com.github.dimitryivaniuta.profile.exception;

/**
 * Raised when event or cache payload serialization fails.
 */
public class SerializationException extends RuntimeException {

    /**
     * Creates a serialization exception.
     *
     * @param message safe error message
     * @param cause original failure
     */
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
