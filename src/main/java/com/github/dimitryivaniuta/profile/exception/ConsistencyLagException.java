package com.github.dimitryivaniuta.profile.exception;

/**
 * Raised when a regional read model is behind a caller's requested minimum profile version.
 */
public class ConsistencyLagException extends RuntimeException {

    /**
     * Creates the exception with a safe client-facing message.
     *
     * @param message message explaining the consistency gap
     */
    public ConsistencyLagException(String message) {
        super(message);
    }
}
