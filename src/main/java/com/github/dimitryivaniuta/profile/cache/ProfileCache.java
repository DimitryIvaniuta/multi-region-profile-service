package com.github.dimitryivaniuta.profile.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.profile.api.ProfileViewResponse;
import com.github.dimitryivaniuta.profile.config.ConsistencyProperties;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import com.github.dimitryivaniuta.profile.exception.SerializationException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Regional Redis cache facade for profile read views.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileCache {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RegionProperties regionProperties;
    private final ConsistencyProperties consistencyProperties;

    /**
     * Reads a cached profile view.
     *
     * @param profileId profile identifier
     * @return cached profile response when present and valid
     */
    public Mono<ProfileViewResponse> get(UUID profileId) {
        return redisTemplate.opsForValue()
                .get(key(profileId))
                .flatMap(this::deserialize)
                .onErrorResume(error -> {
                    log.warn("Redis profile cache read failed for profileId={}: {}", profileId, error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Stores a regional profile view in Redis with configured TTL.
     *
     * @param response profile response to cache
     * @return completion signal
     */
    public Mono<Void> put(ProfileViewResponse response) {
        return serialize(response)
                .flatMap(json -> redisTemplate.opsForValue().set(key(response.profileId()), json, consistencyProperties.cacheTtl()))
                .then()
                .onErrorResume(error -> {
                    log.warn("Redis profile cache write failed for profileId={}: {}", response.profileId(), error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Evicts a regional profile view.
     *
     * @param profileId profile identifier
     * @return completion signal
     */
    public Mono<Void> evict(UUID profileId) {
        return redisTemplate.delete(key(profileId)).then();
    }

    /**
     * Builds a cache key with region and schema version in the prefix.
     *
     * @param profileId profile identifier
     * @return Redis key
     */
    public String key(UUID profileId) {
        return "profile:v1:" + regionProperties.name() + ":" + profileId;
    }

    private Mono<ProfileViewResponse> deserialize(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, ProfileViewResponse.class));
        } catch (JsonProcessingException exception) {
            return Mono.error(new SerializationException("Unable to deserialize cached profile", exception));
        }
    }

    private Mono<String> serialize(ProfileViewResponse response) {
        try {
            return Mono.just(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException exception) {
            return Mono.error(new SerializationException("Unable to serialize cached profile", exception));
        }
    }
}
