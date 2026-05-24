package com.github.dimitryivaniuta.profile.persistence;

import java.time.Instant;

/**
 * Last consumed Kafka position for a profile projection partition in one region.
 *
 * @param topicName Kafka topic name
 * @param partitionId Kafka partition identifier
 * @param currentOffset latest consumed offset for this partition
 * @param latestEventTime event timestamp carried by the latest consumed event
 * @param lastConsumedAt local timestamp when the latest record was consumed
 * @param region region that owns this read model
 */
public record ProjectionWatermarkRow(
        String topicName,
        int partitionId,
        long currentOffset,
        Instant latestEventTime,
        Instant lastConsumedAt,
        String region
) {
}
