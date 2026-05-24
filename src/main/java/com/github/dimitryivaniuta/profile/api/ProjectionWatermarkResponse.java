package com.github.dimitryivaniuta.profile.api;

import java.time.Instant;

/**
 * API response for one regional Kafka projection watermark.
 *
 * @param topicName Kafka topic name
 * @param partitionId Kafka partition id
 * @param currentOffset latest consumed offset
 * @param latestEventTime timestamp carried by the latest consumed event
 * @param lastConsumedAt local consumption timestamp
 * @param region region that owns the projection
 */
public record ProjectionWatermarkResponse(
        String topicName,
        int partitionId,
        long currentOffset,
        Instant latestEventTime,
        Instant lastConsumedAt,
        String region
) {
}
