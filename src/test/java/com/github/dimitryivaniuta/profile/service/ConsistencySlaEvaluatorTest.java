package com.github.dimitryivaniuta.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for eventual consistency SLA calculations.
 */
class ConsistencySlaEvaluatorTest {

    private final ConsistencySlaEvaluator evaluator = new ConsistencySlaEvaluator();

    /**
     * Lag is calculated from source event time to observation time.
     */
    @Test
    void lagSecondsShouldUseEventTimeAndObservedTime() {
        Instant eventTime = Instant.parse("2026-05-22T10:00:00Z");
        Instant observedAt = Instant.parse("2026-05-22T10:00:04Z");

        assertThat(evaluator.lagSeconds(eventTime, observedAt)).isEqualTo(4);
    }

    /**
     * Clock skew should not produce negative lag in API responses.
     */
    @Test
    void lagSecondsShouldNeverBeNegative() {
        Instant eventTime = Instant.parse("2026-05-22T10:00:10Z");
        Instant observedAt = Instant.parse("2026-05-22T10:00:04Z");

        assertThat(evaluator.lagSeconds(eventTime, observedAt)).isZero();
    }

    /**
     * SLA is inclusive at the exact configured boundary.
     */
    @Test
    void withinSlaShouldBeInclusive() {
        Instant eventTime = Instant.parse("2026-05-22T10:00:00Z");
        Instant observedAt = Instant.parse("2026-05-22T10:00:05Z");

        assertThat(evaluator.withinSla(eventTime, observedAt, Duration.ofSeconds(5))).isTrue();
    }
}
