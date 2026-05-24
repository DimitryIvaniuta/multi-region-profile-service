package com.github.dimitryivaniuta.profile.api;

import java.time.Instant;

/**
 * Operational response for regional replication lag checks.
 *
 * @param region serving region
 * @param role serving role
 * @param latestEventTime timestamp of the newest consumed/projected event
 * @param measuredAt timestamp when lag was calculated
 * @param lagSeconds lag in seconds or null when no event has been consumed yet
 * @param visibilitySlaSeconds configured visibility SLA in seconds
 * @param withinSla whether lag is within the configured SLA
 * @param lagSource source used to calculate lag, for example WATERMARK, READ_MODEL or EMPTY
 */
public record ReplicationLagResponse(
        String region,
        String role,
        Instant latestEventTime,
        Instant measuredAt,
        Long lagSeconds,
        long visibilitySlaSeconds,
        boolean withinSla,
        String lagSource
) {
}
