package com.github.dimitryivaniuta.profile.service;

import com.github.dimitryivaniuta.profile.cache.ProfileCache;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import com.github.dimitryivaniuta.profile.event.ProfileChangeEvent;
import com.github.dimitryivaniuta.profile.exception.SerializationException;
import com.github.dimitryivaniuta.profile.persistence.InvalidProfileEventRow;
import com.github.dimitryivaniuta.profile.persistence.ProfileReadModelRow;
import com.github.dimitryivaniuta.profile.persistence.ProfileStore;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Applies profile change events to a regional read model in an idempotent and ordered way.
 *
 * <p>Projection processing records Kafka partition watermarks so operational lag reflects the
 * consumer position, not only the newest row in the read model. Malformed events are quarantined in
 * Postgres instead of being retried forever.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileProjectionService {

    private static final String UNKNOWN_TOPIC = "unknown";

    private final ProfileMapper profileMapper;
    private final ProfileStore profileStore;
    private final ProfileCache profileCache;
    private final RegionProperties regionProperties;

    /**
     * Parses and applies a serialized Kafka profile event when Kafka metadata is not available.
     *
     * @param payload Kafka record value
     * @return completion signal
     */
    public Mono<Void> apply(String payload) {
        return apply(payload, UNKNOWN_TOPIC, 0, 0L, null);
    }

    /**
     * Parses and applies a serialized Kafka profile event with source Kafka metadata.
     *
     * @param payload Kafka record value
     * @param topicName Kafka topic name
     * @param partitionId Kafka partition id
     * @param offset Kafka record offset
     * @param recordKey Kafka record key
     * @return completion signal
     */
    public Mono<Void> apply(String payload, String topicName, int partitionId, long offset, String recordKey) {
        Instant consumedAt = Instant.now();
        ProfileChangeEvent event;
        try {
            event = profileMapper.fromJson(payload);
        } catch (SerializationException exception) {
            log.warn("Quarantining invalid profile event topic={} partition={} offset={} error={}",
                    topicName, partitionId, offset, exception.getMessage());
            return profileStore.insertInvalidProfileEvent(new InvalidProfileEventRow(
                    null,
                    topicName,
                    partitionId,
                    offset,
                    recordKey,
                    payload,
                    exception.getMessage(),
                    consumedAt,
                    regionProperties.name()
            ));
        }

        return profileStore.insertConsumedEvent(event.eventId(), event.profileId(), event.sourceRegion(), consumedAt)
                .flatMap(firstSeen -> applyFirstSeenEventIfNeeded(firstSeen, event, consumedAt))
                .then(profileStore.upsertProjectionWatermark(
                        topicName,
                        partitionId,
                        offset,
                        event.occurredAt(),
                        consumedAt,
                        regionProperties.name()
                ));
    }

    private Mono<Void> applyFirstSeenEventIfNeeded(boolean firstSeen, ProfileChangeEvent event, Instant consumedAt) {
        if (!firstSeen) {
            log.debug("Ignoring duplicate profile event eventId={} in region={}", event.eventId(), regionProperties.name());
            return Mono.empty();
        }
        ProfileReadModelRow row = profileMapper.toReadModel(event, consumedAt);
        return profileStore.upsertReadModel(row)
                .flatMap(applied -> {
                    if (!applied) {
                        log.info("Ignoring stale profile event eventId={} profileId={} version={} in region={}",
                                event.eventId(), event.profileId(), event.profileVersion(), regionProperties.name());
                        return Mono.empty();
                    }
                    return profileCache.evict(event.profileId());
                });
    }
}
