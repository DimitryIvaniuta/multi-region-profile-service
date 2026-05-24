package com.github.dimitryivaniuta.profile.api;

import java.time.Instant;

/**
 * API response for one quarantined malformed profile event.
 *
 * @param id generated database identifier
 * @param topicName Kafka topic name
 * @param partitionId Kafka partition id
 * @param recordOffset Kafka record offset
 * @param recordKey Kafka record key
 * @param error safe parse or validation error
 * @param occurredAt local detection timestamp
 * @param region region that detected the invalid event
 */
public record InvalidProfileEventResponse(
        Long id,
        String topicName,
        int partitionId,
        long recordOffset,
        String recordKey,
        String error,
        Instant occurredAt,
        String region
) {
}
