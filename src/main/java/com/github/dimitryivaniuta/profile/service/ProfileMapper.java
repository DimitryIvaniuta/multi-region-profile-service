package com.github.dimitryivaniuta.profile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.profile.api.ProfileResponse;
import com.github.dimitryivaniuta.profile.api.ProfileViewResponse;
import com.github.dimitryivaniuta.profile.config.ConsistencyProperties;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import com.github.dimitryivaniuta.profile.domain.OutboxStatus;
import com.github.dimitryivaniuta.profile.domain.ProfileEventType;
import com.github.dimitryivaniuta.profile.event.ProfileChangeEvent;
import com.github.dimitryivaniuta.profile.event.ProfileSnapshot;
import com.github.dimitryivaniuta.profile.exception.SerializationException;
import com.github.dimitryivaniuta.profile.persistence.OutboxRow;
import com.github.dimitryivaniuta.profile.persistence.ProfileReadModelRow;
import com.github.dimitryivaniuta.profile.persistence.ProfileRow;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps between persistence rows, API responses and integration events.
 */
@Component
@RequiredArgsConstructor
public class ProfileMapper {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final RegionProperties regionProperties;
    private final ConsistencyProperties consistencyProperties;
    private final ConsistencySlaEvaluator slaEvaluator;

    /**
     * Converts the write-model row into a synchronous API response.
     *
     * @param row write-model row
     * @return API response
     */
    public ProfileResponse toProfileResponse(ProfileRow row) {
        return new ProfileResponse(
                row.id(),
                row.email(),
                row.displayName(),
                row.phone(),
                row.status(),
                row.version(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    /**
     * Builds a versioned profile event from the new aggregate state.
     *
     * @param row updated profile row
     * @param type event type
     * @param now event timestamp
     * @return profile change event
     */
    public ProfileChangeEvent toEvent(ProfileRow row, ProfileEventType type, Instant now) {
        return new ProfileChangeEvent(
                CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                type,
                row.id(),
                row.version(),
                regionProperties.name(),
                now,
                new ProfileSnapshot(row.id(), row.email(), row.displayName(), row.phone(), row.status()),
                Map.of("producer", "multi-region-profile-service")
        );
    }

    /**
     * Serializes an event into a transactional outbox row.
     *
     * @param event event to publish
     * @return outbox row
     */
    public OutboxRow toOutbox(ProfileChangeEvent event) {
        return new OutboxRow(
                event.eventId(),
                event.profileId(),
                event.profileVersion(),
                event.eventType(),
                toJson(event),
                OutboxStatus.NEW,
                0,
                null,
                event.occurredAt(),
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Parses an event payload consumed from Kafka.
     *
     * @param payload serialized event payload
     * @return parsed profile change event
     */
    public ProfileChangeEvent fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, ProfileChangeEvent.class);
        } catch (JsonProcessingException exception) {
            throw new SerializationException("Unable to parse profile event", exception);
        }
    }

    /**
     * Converts an event into a regional read-model row.
     *
     * @param event event to project
     * @param replicatedAt local projection timestamp
     * @return read-model row
     */
    public ProfileReadModelRow toReadModel(ProfileChangeEvent event, Instant replicatedAt) {
        return new ProfileReadModelRow(
                event.profileId(),
                event.profile().email(),
                event.profile().displayName(),
                event.profile().phone(),
                event.profile().status(),
                event.profileVersion(),
                event.sourceRegion(),
                event.eventId(),
                event.occurredAt(),
                replicatedAt
        );
    }

    /**
     * Converts a regional read-model row into a response that exposes consistency metadata.
     *
     * @param row regional read-model row
     * @param observedAt timestamp of the read operation
     * @return read response
     */
    public ProfileViewResponse toProfileView(ProfileReadModelRow row, Instant observedAt) {
        long lagSeconds = slaEvaluator.lagSeconds(row.eventTime(), observedAt);
        return new ProfileViewResponse(
                row.profileId(),
                row.email(),
                row.displayName(),
                row.phone(),
                row.status(),
                row.version(),
                row.sourceRegion(),
                row.eventId(),
                row.eventTime(),
                row.replicatedAt(),
                lagSeconds,
                lagSeconds <= consistencyProperties.visibilitySla().toSeconds()
        );
    }


    /**
     * Recomputes dynamic consistency metadata for a cached profile response.
     *
     * @param response cached profile response
     * @param observedAt timestamp of the current read operation
     * @return response with refreshed lag metadata
     */
    public ProfileViewResponse refreshConsistency(ProfileViewResponse response, Instant observedAt) {
        long lagSeconds = slaEvaluator.lagSeconds(response.eventTime(), observedAt);
        return new ProfileViewResponse(
                response.profileId(),
                response.email(),
                response.displayName(),
                response.phone(),
                response.status(),
                response.version(),
                response.sourceRegion(),
                response.eventId(),
                response.eventTime(),
                response.replicatedAt(),
                lagSeconds,
                lagSeconds <= consistencyProperties.visibilitySla().toSeconds()
        );
    }

    private String toJson(ProfileChangeEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new SerializationException("Unable to serialize profile event", exception);
        }
    }
}
