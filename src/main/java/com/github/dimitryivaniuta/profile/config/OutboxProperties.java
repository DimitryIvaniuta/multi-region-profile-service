package com.github.dimitryivaniuta.profile.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Transactional outbox publisher settings.
 *
 * @param enabled whether scheduled publication is enabled
 * @param batchSize maximum rows claimed per publisher tick
 * @param maxAttempts maximum Kafka publish attempts before the event is quarantined
 * @param fixedDelayMs delay between scheduled publisher ticks
 * @param retryBackoffBase lower bound for failed publish retry backoff
 * @param retryBackoffMax upper bound for failed publish retry backoff
 * @param lockTimeout timeout after which an in-progress row can be reclaimed by another publisher
 */
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        boolean enabled,
        int batchSize,
        int maxAttempts,
        long fixedDelayMs,
        Duration retryBackoffBase,
        Duration retryBackoffMax,
        Duration lockTimeout
) {
}
