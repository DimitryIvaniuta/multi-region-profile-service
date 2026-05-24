package com.github.dimitryivaniuta.profile.service;

import com.github.dimitryivaniuta.profile.api.ProfileViewResponse;
import com.github.dimitryivaniuta.profile.cache.ProfileCache;
import com.github.dimitryivaniuta.profile.exception.ConsistencyLagException;
import com.github.dimitryivaniuta.profile.exception.NotFoundException;
import com.github.dimitryivaniuta.profile.persistence.ProfileStore;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Serves low-latency regional reads from Redis and local Postgres read model.
 */
@Service
@RequiredArgsConstructor
public class ProfileReadService {

    private final ProfileCache profileCache;
    private final ProfileStore profileStore;
    private final ProfileMapper profileMapper;

    /**
     * Reads a profile view from regional cache or local read-model database.
     *
     * @param profileId profile identifier
     * @return eventually consistent profile view
     */
    public Mono<ProfileViewResponse> getProfile(UUID profileId) {
        return getProfile(profileId, null);
    }

    /**
     * Reads a profile view and optionally enforces a read-your-writes minimum version.
     *
     * <p>The {@code minVersion} guard lets clients avoid silently accepting stale data after a write.
     * If the local read model has not caught up yet, the service returns a consistency-lag conflict
     * instead of returning an older profile version.</p>
     *
     * @param profileId profile identifier
     * @param minVersion minimum acceptable profile version, or null when any version is acceptable
     * @return eventually consistent profile view that satisfies the requested version
     */
    public Mono<ProfileViewResponse> getProfile(UUID profileId, Long minVersion) {
        return readProfile(profileId)
                .flatMap(response -> ensureMinimumVersion(response, minVersion));
    }

    private Mono<ProfileViewResponse> readProfile(UUID profileId) {
        return profileCache.get(profileId)
                .map(cached -> profileMapper.refreshConsistency(cached, Instant.now()))
                .switchIfEmpty(Mono.defer(() -> profileStore.findReadModel(profileId)
                        .map(row -> profileMapper.toProfileView(row, Instant.now()))
                        .flatMap(response -> profileCache.put(response).thenReturn(response))))
                .switchIfEmpty(Mono.error(new NotFoundException("Profile read model not found in this region")));
    }

    private Mono<ProfileViewResponse> ensureMinimumVersion(ProfileViewResponse response, Long minVersion) {
        if (minVersion == null || response.version() >= minVersion) {
            return Mono.just(response);
        }
        return Mono.error(new ConsistencyLagException(
                "Regional read model is behind requested profile version " + minVersion
                        + "; current version is " + response.version()
        ));
    }
}
