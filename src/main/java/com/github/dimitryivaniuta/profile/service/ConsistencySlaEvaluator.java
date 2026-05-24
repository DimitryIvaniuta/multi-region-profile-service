package com.github.dimitryivaniuta.profile.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Evaluates whether an eventually consistent read is inside the promised visibility SLA.
 */
@Component
public class ConsistencySlaEvaluator {

    /**
     * Calculates non-negative lag in seconds between source event time and current observation time.
     *
     * @param eventTime source event timestamp
     * @param observedAt time at which the read is observed
     * @return non-negative lag in seconds
     */
    public long lagSeconds(Instant eventTime, Instant observedAt) {
        if (eventTime == null || observedAt == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, Duration.between(eventTime, observedAt).toSeconds());
    }

    /**
     * Checks if an observed read is within the configured SLA.
     *
     * @param eventTime source event timestamp
     * @param observedAt observation timestamp
     * @param sla visibility SLA
     * @return true when the observed lag is less than or equal to the SLA
     */
    public boolean withinSla(Instant eventTime, Instant observedAt, Duration sla) {
        return lagSeconds(eventTime, observedAt) <= sla.toSeconds();
    }
}
