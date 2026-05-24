package com.github.dimitryivaniuta.profile.api;

import com.github.dimitryivaniuta.profile.domain.ProfileStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Strongly consistent response returned from the primary write model.
 *
 * @param profileId profile identifier
 * @param email email address
 * @param displayName profile name
 * @param phone optional phone number
 * @param status profile lifecycle status
 * @param version monotonically increasing profile version
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record ProfileResponse(
        UUID profileId,
        String email,
        String displayName,
        String phone,
        ProfileStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
