package com.github.dimitryivaniuta.profile.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis-backed fixed-window rate limit settings for API protection.
 *
 * @param enabled whether Redis rate limiting is active
 * @param window fixed window size used for counting requests
 * @param readLimit maximum read requests per identity and window
 * @param writeLimit maximum write requests per identity and window
 * @param internalLimit maximum internal operation requests per identity and window
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        Duration window,
        long readLimit,
        long writeLimit,
        long internalLimit
) {
}
