package com.github.dimitryivaniuta.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.dimitryivaniuta.profile.config.ConsistencyProperties;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import com.github.dimitryivaniuta.profile.config.RegionRole;
import com.github.dimitryivaniuta.profile.domain.ProfileEventType;
import com.github.dimitryivaniuta.profile.domain.ProfileStatus;
import com.github.dimitryivaniuta.profile.event.ProfileChangeEvent;
import com.github.dimitryivaniuta.profile.persistence.ProfileRow;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for event and API mapping.
 */
class ProfileMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ProfileMapper mapper = new ProfileMapper(
            objectMapper,
            new RegionProperties("eu-central-1", RegionRole.PRIMARY),
            new ConsistencyProperties(Duration.ofSeconds(5), Duration.ofSeconds(60)),
            new ConsistencySlaEvaluator()
    );

    /**
     * Profile events must carry a full snapshot and a stable schema version.
     */
    @Test
    void toEventShouldIncludeSnapshotAndVersion() {
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.parse("2026-05-22T10:00:00Z");
        ProfileRow row = new ProfileRow(profileId, "alice@example.com", "Alice", null, ProfileStatus.ACTIVE, 1, now, now);

        ProfileChangeEvent event = mapper.toEvent(row, ProfileEventType.PROFILE_CREATED, now);

        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.profileId()).isEqualTo(profileId);
        assertThat(event.profile().email()).isEqualTo("alice@example.com");
        assertThat(event.sourceRegion()).isEqualTo("eu-central-1");
    }

    /**
     * Outbox payload must round-trip to the original event shape.
     */
    @Test
    void outboxPayloadShouldRoundTrip() {
        UUID profileId = UUID.randomUUID();
        Instant now = Instant.parse("2026-05-22T10:00:00Z");
        ProfileRow row = new ProfileRow(profileId, "alice@example.com", "Alice", null, ProfileStatus.ACTIVE, 1, now, now);
        ProfileChangeEvent event = mapper.toEvent(row, ProfileEventType.PROFILE_CREATED, now);

        ProfileChangeEvent parsed = mapper.fromJson(mapper.toOutbox(event).payload());

        assertThat(parsed.eventId()).isEqualTo(event.eventId());
        assertThat(parsed.profile().displayName()).isEqualTo("Alice");
    }
}
