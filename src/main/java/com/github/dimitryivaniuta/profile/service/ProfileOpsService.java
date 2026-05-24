package com.github.dimitryivaniuta.profile.service;

import com.github.dimitryivaniuta.profile.api.InvalidProfileEventResponse;
import com.github.dimitryivaniuta.profile.api.OutboxStatsResponse;
import com.github.dimitryivaniuta.profile.api.ProjectionWatermarkResponse;
import com.github.dimitryivaniuta.profile.persistence.InvalidProfileEventRow;
import com.github.dimitryivaniuta.profile.persistence.OutboxStatusCountRow;
import com.github.dimitryivaniuta.profile.persistence.ProfileStore;
import com.github.dimitryivaniuta.profile.persistence.ProjectionWatermarkRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Read-only operational queries used by internal SRE endpoints.
 */
@Service
@RequiredArgsConstructor
public class ProfileOpsService {

    private final ProfileStore profileStore;

    /**
     * Returns current Kafka projection watermarks.
     *
     * @return ordered list of watermarks
     */
    public Mono<List<ProjectionWatermarkResponse>> getProjectionWatermarks() {
        return profileStore.findProjectionWatermarks()
                .map(this::toWatermarkResponse)
                .collectList();
    }

    /**
     * Returns recent invalid events quarantined by the projection consumer.
     *
     * @param limit maximum rows to return
     * @return ordered list of invalid events
     */
    public Mono<List<InvalidProfileEventResponse>> getInvalidEvents(int limit) {
        return profileStore.findInvalidProfileEvents(limit)
                .map(this::toInvalidEventResponse)
                .collectList();
    }

    /**
     * Returns transactional outbox counters by status.
     *
     * @return outbox status counters
     */
    public Mono<OutboxStatsResponse> getOutboxStats() {
        return profileStore.countOutboxByStatus()
                .collect(
                        LinkedHashMap<String, Long>::new,
                        (map, row) -> map.put(row.status().name(), row.count())
                )
                .map(map -> new OutboxStatsResponse(Map.copyOf(map)));
    }

    private ProjectionWatermarkResponse toWatermarkResponse(ProjectionWatermarkRow row) {
        return new ProjectionWatermarkResponse(
                row.topicName(),
                row.partitionId(),
                row.currentOffset(),
                row.latestEventTime(),
                row.lastConsumedAt(),
                row.region()
        );
    }

    private InvalidProfileEventResponse toInvalidEventResponse(InvalidProfileEventRow row) {
        return new InvalidProfileEventResponse(
                row.id(),
                row.topicName(),
                row.partitionId(),
                row.recordOffset(),
                row.recordKey(),
                row.error(),
                row.occurredAt(),
                row.region()
        );
    }
}
