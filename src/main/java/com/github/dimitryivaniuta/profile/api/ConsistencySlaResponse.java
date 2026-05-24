package com.github.dimitryivaniuta.profile.api;

/**
 * API response that describes the configured eventual consistency contract.
 *
 * @param region serving region
 * @param role serving role
 * @param visibilitySlaSeconds maximum expected propagation time in seconds
 * @param cacheTtlSeconds regional Redis cache TTL in seconds
 * @param model consistency model name
 */
public record ConsistencySlaResponse(
        String region,
        String role,
        long visibilitySlaSeconds,
        long cacheTtlSeconds,
        String model
) {
}
