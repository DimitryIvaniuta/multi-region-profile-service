package com.github.dimitryivaniuta.profile.service;

import com.github.dimitryivaniuta.profile.config.KafkaTopicProperties;
import com.github.dimitryivaniuta.profile.config.OutboxProperties;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import com.github.dimitryivaniuta.profile.persistence.OutboxRow;
import com.github.dimitryivaniuta.profile.persistence.ProfileStore;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Publishes transactional outbox records to Kafka from the primary region.
 *
 * <p>Rows are atomically claimed before publication, which keeps the scheduler safe when several
 * primary-region pods are running at the same time. Failed publications receive exponential backoff
 * and move to {@code EXHAUSTED} after the configured retry budget.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileOutboxPublisher {

    private final ProfileStore profileStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;
    private final OutboxProperties outboxProperties;
    private final RegionProperties regionProperties;
    private final String publisherInstanceId = "profile-outbox-" + UUID.randomUUID();

    /**
     * Scheduled outbox publisher tick. It is disabled automatically in read-replica regions.
     */
    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:1000}")
    public void publishScheduled() {
        if (!outboxProperties.enabled() || !regionProperties.isPrimary()) {
            return;
        }
        publishBatch(outboxProperties.batchSize())
                .doOnError(error -> log.error("Outbox publisher batch failed: {}", error.getMessage(), error))
                .subscribe();
    }

    /**
     * Publishes at most {@code limit} outbox rows.
     *
     * @param limit maximum rows to publish
     * @return number of events submitted successfully
     */
    public Mono<Integer> publishBatch(int limit) {
        if (!regionProperties.isPrimary()) {
            return Mono.just(0);
        }
        int effectiveLimit = Math.max(1, Math.min(limit, outboxProperties.batchSize()));
        Instant now = Instant.now();
        Instant staleLockBefore = now.minus(outboxProperties.lockTimeout());
        return profileStore.claimPublishableOutbox(
                        outboxProperties.maxAttempts(),
                        effectiveLimit,
                        publisherInstanceId,
                        now,
                        staleLockBefore
                )
                .flatMap(this::publishOne, 8)
                .reduce(0, Integer::sum);
    }

    private Mono<Integer> publishOne(OutboxRow row) {
        return Mono.fromFuture(kafkaTemplate.send(
                        kafkaTopicProperties.profileChangesTopic(),
                        row.aggregateId().toString(),
                        row.payload()
                ))
                .flatMap(result -> profileStore.markOutboxPublished(row.eventId(), Instant.now()))
                .thenReturn(1)
                .onErrorResume(error -> {
                    int nextAttemptNumber = row.attempts() + 1;
                    boolean exhausted = nextAttemptNumber >= outboxProperties.maxAttempts();
                    Instant failedAt = Instant.now();
                    Instant nextAttemptAt = failedAt.plus(backoffFor(nextAttemptNumber));
                    if (exhausted) {
                        log.error("Profile outbox event exhausted retry budget eventId={} attempts={} error={}",
                                row.eventId(), nextAttemptNumber, error.getMessage(), error);
                    } else {
                        log.warn("Profile outbox event publish failed eventId={} attempts={} nextAttemptAt={} error={}",
                                row.eventId(), nextAttemptNumber, nextAttemptAt, error.getMessage());
                    }
                    return profileStore.markOutboxFailed(
                            row.eventId(),
                            error.getMessage(),
                            nextAttemptAt,
                            exhausted,
                            failedAt
                    ).thenReturn(0);
                });
    }

    private Duration backoffFor(int attemptNumber) {
        long baseMillis = outboxProperties.retryBackoffBase().toMillis();
        long maxMillis = outboxProperties.retryBackoffMax().toMillis();
        long multiplier = 1L << Math.min(Math.max(attemptNumber - 1, 0), 10);
        long exponentialMillis = Math.min(baseMillis * multiplier, maxMillis);
        long jitterMillis = ThreadLocalRandom.current().nextLong(Math.max(1, exponentialMillis / 4));
        return Duration.ofMillis(Math.min(exponentialMillis + jitterMillis, maxMillis));
    }
}
