package com.github.dimitryivaniuta.profile.persistence;

import java.time.Instant;

/**
 * Quarantined malformed profile event captured for operator inspection.
 *
 * @param id generated database identifier
 * @param topicName Kafka topic name
 * @param partitionId Kafka partition identifier
 * @param recordOffset Kafka record offset
 * @param recordKey Kafka record key
 * @param payload raw event payload
 * @param error safe parse or validation error message
 * @param occurredAt local timestamp when the invalid event was detected
 * @param region region that detected the invalid event
 */
public record InvalidProfileEventRow(
        Long id,
        String topicName,
        int partitionId,
        long recordOffset,
        String recordKey,
        String payload,
        String error,
        Instant occurredAt,
        String region
) {
}
