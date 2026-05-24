package com.github.dimitryivaniuta.profile.event;

import com.github.dimitryivaniuta.profile.domain.ProfileEventType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned event emitted by the primary region when profile state changes.
 *
 * @param schemaVersion event schema version for forward-compatible consumers
 * @param eventId unique event identifier used for idempotency
 * @param eventType semantic event type
 * @param profileId aggregate identifier and Kafka message key
 * @param profileVersion monotonically increasing profile version
 * @param sourceRegion region that produced the event
 * @param occurredAt event creation timestamp in the primary region
 * @param profile complete profile snapshot after the change
 * @param metadata non-sensitive operational attributes
 */
public record ProfileChangeEvent(
        int schemaVersion,
        UUID eventId,
        ProfileEventType eventType,
        UUID profileId,
        long profileVersion,
        String sourceRegion,
        Instant occurredAt,
        ProfileSnapshot profile,
        Map<String, String> metadata
) {
}
