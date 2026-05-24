package com.github.dimitryivaniuta.profile.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Consistency and cache settings for regional reads.
 *
 * @param visibilitySla maximum expected delay before a primary write is visible in read regions
 * @param cacheTtl Redis TTL for profile views cached in each region
 */
@ConfigurationProperties(prefix = "app.consistency")
public record ConsistencyProperties(Duration visibilitySla, Duration cacheTtl) {
}
