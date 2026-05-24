package com.github.dimitryivaniuta.profile.persistence;

import com.github.dimitryivaniuta.profile.domain.OutboxStatus;

/**
 * Aggregated count of transactional outbox records by delivery status.
 *
 * @param status outbox delivery status
 * @param count number of rows in that status
 */
public record OutboxStatusCountRow(OutboxStatus status, long count) {
}
