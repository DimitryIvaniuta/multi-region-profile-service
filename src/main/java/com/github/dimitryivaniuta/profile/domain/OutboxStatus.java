package com.github.dimitryivaniuta.profile.domain;

/**
 * Delivery status of a transactional outbox record.
 */
public enum OutboxStatus {
    /** Record is waiting for initial Kafka publication. */
    NEW,

    /** Record is claimed by one publisher instance and is being sent to Kafka. */
    IN_PROGRESS,

    /** Last publication attempt failed and can be retried after its backoff window. */
    FAILED,

    /** Record exhausted all retry attempts and requires operator investigation. */
    EXHAUSTED,

    /** Record has been successfully acknowledged by Kafka. */
    PUBLISHED
}
