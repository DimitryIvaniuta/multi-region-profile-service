package com.github.dimitryivaniuta.profile.api;

import com.github.dimitryivaniuta.profile.domain.ProfileStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Eventually consistent profile view returned by regional read endpoints.
 *
 * @param profileId profile identifier
 * @param email email address
 * @param displayName profile name
 * @param phone optional phone number
 * @param status profile lifecycle status
 * @param version projected profile version
 * @param sourceRegion region that produced the event
 * @param eventId source event identifier applied to this view
 * @param eventTime source event timestamp
 * @param replicatedAt regional projection timestamp
 * @param replicationLagSeconds lag between event time and regional read time
 * @param withinSla whether this profile view is within the configured visibility SLA
 */
public record ProfileViewResponse(
        UUID profileId,
        String email,
        String displayName,
        String phone,
        ProfileStatus status,
        long version,
        String sourceRegion,
        UUID eventId,
        Instant eventTime,
        Instant replicatedAt,
        long replicationLagSeconds,
        boolean withinSla
) {
}
