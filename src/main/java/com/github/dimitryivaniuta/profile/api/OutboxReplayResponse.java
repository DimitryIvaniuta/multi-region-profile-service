package com.github.dimitryivaniuta.profile.api;

/**
 * Response returned after an operator asks the service to publish a batch of outbox rows.
 *
 * @param requestedLimit maximum batch size requested by the caller
 * @param submitted number of events submitted to Kafka in this replay attempt
 */
public record OutboxReplayResponse(int requestedLimit, int submitted) {
}
