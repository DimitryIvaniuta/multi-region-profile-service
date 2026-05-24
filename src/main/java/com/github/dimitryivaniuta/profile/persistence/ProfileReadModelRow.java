package com.github.dimitryivaniuta.profile.persistence;

import com.github.dimitryivaniuta.profile.domain.ProfileStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Row representation of a regional profile read model.
 *
 * @param profileId profile identifier
 * @param email email address
 * @param displayName display name
 * @param phone optional phone number
 * @param status lifecycle status
 * @param version projected aggregate version
 * @param sourceRegion source event region
 * @param eventId applied source event identifier
 * @param eventTime source event time
 * @param replicatedAt projection timestamp in this read region
 */
public record ProfileReadModelRow(
        UUID profileId,
        String email,
        String displayName,
        String phone,
        ProfileStatus status,
        long version,
        String sourceRegion,
        UUID eventId,
        Instant eventTime,
        Instant replicatedAt
) {
}
