package com.github.dimitryivaniuta.profile.api;

import java.util.Map;

/**
 * Operational outbox counters grouped by delivery status.
 *
 * @param countsByStatus count of rows per transactional outbox status
 */
public record OutboxStatsResponse(Map<String, Long> countsByStatus) {
}
