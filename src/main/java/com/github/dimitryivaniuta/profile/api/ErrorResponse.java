package com.github.dimitryivaniuta.profile.api;

import java.time.Instant;

/**
 * Standard error body returned by global exception handling.
 *
 * @param code stable machine-readable error code
 * @param message safe human-readable message
 * @param path request path
 * @param timestamp response timestamp
 */
public record ErrorResponse(String code, String message, String path, Instant timestamp) {
}
