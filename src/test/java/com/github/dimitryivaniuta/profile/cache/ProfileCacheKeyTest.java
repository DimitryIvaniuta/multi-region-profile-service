package com.github.dimitryivaniuta.profile.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.profile.config.ConsistencyProperties;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import com.github.dimitryivaniuta.profile.config.RegionRole;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Unit test for region-scoped Redis cache key format.
 */
class ProfileCacheKeyTest {

    /**
     * Regional keys prevent one region from accidentally sharing stale entries with another.
     */
    @Test
    void keyShouldContainRegionAndSchemaVersion() {
        ProfileCache cache = new ProfileCache(
                Mockito.mock(ReactiveStringRedisTemplate.class),
                new ObjectMapper(),
                new RegionProperties("us-east-1", RegionRole.READ_REPLICA),
                new ConsistencyProperties(Duration.ofSeconds(5), Duration.ofSeconds(60))
        );
        UUID profileId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(cache.key(profileId)).isEqualTo("profile:v1:us-east-1:11111111-1111-1111-1111-111111111111");
    }
}
