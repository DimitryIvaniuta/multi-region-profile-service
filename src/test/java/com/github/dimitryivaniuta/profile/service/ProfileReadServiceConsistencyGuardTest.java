package com.github.dimitryivaniuta.profile.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.dimitryivaniuta.profile.api.ProfileViewResponse;
import com.github.dimitryivaniuta.profile.cache.ProfileCache;
import com.github.dimitryivaniuta.profile.domain.ProfileStatus;
import com.github.dimitryivaniuta.profile.exception.ConsistencyLagException;
import com.github.dimitryivaniuta.profile.persistence.ProfileStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for read-your-writes minimum-version guard on regional reads.
 */
@ExtendWith(MockitoExtension.class)
class ProfileReadServiceConsistencyGuardTest {

    @Mock
    private ProfileCache profileCache;

    @Mock
    private ProfileStore profileStore;

    @Mock
    private ProfileMapper profileMapper;

    /**
     * A caller that knows a newer write version must not receive an older regional view silently.
     */
    @Test
    void getProfileShouldRejectViewBelowMinimumVersion() {
        UUID profileId = UUID.randomUUID();
        ProfileViewResponse stale = new ProfileViewResponse(
                profileId,
                "alice@example.com",
                "Alice",
                null,
                ProfileStatus.ACTIVE,
                2,
                "eu-central-1",
                UUID.randomUUID(),
                Instant.parse("2026-05-22T10:00:00Z"),
                Instant.parse("2026-05-22T10:00:01Z"),
                1,
                true
        );
        when(profileCache.get(profileId)).thenReturn(Mono.just(stale));
        when(profileMapper.refreshConsistency(stale, any())).thenReturn(stale);
        ProfileReadService service = new ProfileReadService(profileCache, profileStore, profileMapper);

        StepVerifier.create(service.getProfile(profileId, 3L))
                .expectError(ConsistencyLagException.class)
                .verify();
    }
}
