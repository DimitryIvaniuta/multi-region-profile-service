package com.github.dimitryivaniuta.profile.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.dimitryivaniuta.profile.cache.ProfileCache;
import com.github.dimitryivaniuta.profile.config.ConsistencyProperties;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import com.github.dimitryivaniuta.profile.config.RegionRole;
import com.github.dimitryivaniuta.profile.domain.ProfileEventType;
import com.github.dimitryivaniuta.profile.domain.ProfileStatus;
import com.github.dimitryivaniuta.profile.event.ProfileChangeEvent;
import com.github.dimitryivaniuta.profile.event.ProfileSnapshot;
import com.github.dimitryivaniuta.profile.persistence.InvalidProfileEventRow;
import com.github.dimitryivaniuta.profile.persistence.ProfileReadModelRow;
import com.github.dimitryivaniuta.profile.persistence.ProfileStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for read-model projection idempotency behavior.
 */
@ExtendWith(MockitoExtension.class)
class ProfileProjectionServiceTest {

    @Mock
    private ProfileStore profileStore;

    @Mock
    private ProfileCache profileCache;

    /**
     * Duplicate Kafka events are ignored after the consumed-event marker rejects them, but the
     * projection watermark is still advanced.
     *
     * @throws Exception when test serialization fails
     */
    @Test
    void applyShouldIgnoreDuplicateEventAndAdvanceWatermark() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ProfileMapper mapper = mapper(objectMapper);
        ProfileProjectionService service = service(mapper);
        ProfileChangeEvent event = sampleEvent();
        when(profileStore.insertConsumedEvent(eq(event.eventId()), eq(event.profileId()), eq(event.sourceRegion()), any()))
                .thenReturn(Mono.just(false));
        when(profileStore.upsertProjectionWatermark(anyString(), anyInt(), anyLong(), any(), any(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.apply(objectMapper.writeValueAsString(event), "profile.changes.v1", 2, 15, event.profileId().toString()))
                .verifyComplete();

        verify(profileStore, never()).upsertReadModel(any(ProfileReadModelRow.class));
        verify(profileCache, never()).evict(any());
        verify(profileStore).upsertProjectionWatermark(eq("profile.changes.v1"), eq(2), eq(15L), eq(event.occurredAt()), any(), eq("eu-west-1"));
    }

    /**
     * A first-seen event updates the read model, evicts stale cache and advances the watermark.
     *
     * @throws Exception when test serialization fails
     */
    @Test
    void applyShouldProjectFirstSeenEvent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ProfileMapper mapper = mapper(objectMapper);
        ProfileProjectionService service = service(mapper);
        ProfileChangeEvent event = sampleEvent();
        when(profileStore.insertConsumedEvent(eq(event.eventId()), eq(event.profileId()), eq(event.sourceRegion()), any()))
                .thenReturn(Mono.just(true));
        when(profileStore.upsertReadModel(any(ProfileReadModelRow.class))).thenReturn(Mono.just(true));
        when(profileCache.evict(event.profileId())).thenReturn(Mono.empty());
        when(profileStore.upsertProjectionWatermark(anyString(), anyInt(), anyLong(), any(), any(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.apply(objectMapper.writeValueAsString(event), "profile.changes.v1", 0, 10, null))
                .verifyComplete();

        verify(profileStore).upsertReadModel(any(ProfileReadModelRow.class));
        verify(profileCache).evict(event.profileId());
    }

    /**
     * Malformed events are stored in the invalid-event quarantine instead of being retried forever.
     */
    @Test
    void applyShouldQuarantineInvalidEvent() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ProfileProjectionService service = service(mapper(objectMapper));
        when(profileStore.insertInvalidProfileEvent(any(InvalidProfileEventRow.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.apply("not-json", "profile.changes.v1", 1, 33, "bad-key"))
                .verifyComplete();

        verify(profileStore).insertInvalidProfileEvent(any(InvalidProfileEventRow.class));
        verify(profileStore, never()).upsertReadModel(any(ProfileReadModelRow.class));
    }

    private ProfileProjectionService service(ProfileMapper mapper) {
        return new ProfileProjectionService(
                mapper,
                profileStore,
                profileCache,
                new RegionProperties("eu-west-1", RegionRole.READ_REPLICA)
        );
    }

    private ProfileMapper mapper(ObjectMapper objectMapper) {
        return new ProfileMapper(
                objectMapper,
                new RegionProperties("eu-west-1", RegionRole.READ_REPLICA),
                new ConsistencyProperties(Duration.ofSeconds(5), Duration.ofSeconds(60)),
                new ConsistencySlaEvaluator()
        );
    }

    private ProfileChangeEvent sampleEvent() {
        UUID profileId = UUID.randomUUID();
        return new ProfileChangeEvent(
                1,
                UUID.randomUUID(),
                ProfileEventType.PROFILE_CREATED,
                profileId,
                1,
                "eu-central-1",
                Instant.parse("2026-05-22T10:00:00Z"),
                new ProfileSnapshot(profileId, "alice@example.com", "Alice", null, ProfileStatus.ACTIVE),
                Map.of()
        );
    }
}
