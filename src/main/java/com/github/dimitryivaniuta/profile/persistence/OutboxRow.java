package com.github.dimitryivaniuta.profile.persistence;

import com.github.dimitryivaniuta.profile.domain.OutboxStatus;
import com.github.dimitryivaniuta.profile.domain.ProfileEventType;
import java.time.Instant;
import java.util.UUID;

/**
 * Row representation of the transactional outbox table.
 *
 * @param eventId event identifier
 * @param aggregateId profile identifier
 * @param aggregateVersion aggregate version represented by the event
 * @param eventType event type
 * @param payload serialized event payload
 * @param status publication status
 * @param attempts publication attempts already recorded
 * @param lastError last publish failure message
 * @param createdAt outbox creation timestamp
 * @param publishedAt Kafka acknowledgement timestamp
 * @param nextAttemptAt next time when a failed event is eligible for retry
 * @param lockedBy publisher instance that claimed the row
 * @param lockedAt claim timestamp used to reclaim stale in-progress rows
 * @param deadLetteredAt timestamp when retries were exhausted
 */
public record OutboxRow(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        ProfileEventType eventType,
        String payload,
        OutboxStatus status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant publishedAt,
        Instant nextAttemptAt,
        String lockedBy,
        Instant lockedAt,
        Instant deadLetteredAt
) {
}
