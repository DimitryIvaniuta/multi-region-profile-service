package com.github.dimitryivaniuta.profile.persistence;

import com.github.dimitryivaniuta.profile.domain.ProfileStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Row representation of the primary write-model profile table.
 *
 * @param id profile identifier
 * @param email unique email address
 * @param displayName display name
 * @param phone optional phone number
 * @param status lifecycle status
 * @param version optimistic aggregate version
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record ProfileRow(
        UUID id,
        String email,
        String displayName,
        String phone,
        ProfileStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
