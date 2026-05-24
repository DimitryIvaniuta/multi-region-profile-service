package com.github.dimitryivaniuta.profile.observability;

import com.github.dimitryivaniuta.profile.service.ReplicationLagService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Exposes regional replication lag as a Prometheus/Micrometer gauge.
 */
@Component
public class ProfileMetrics {

    private final ReplicationLagService replicationLagService;
    private final AtomicLong lagSeconds = new AtomicLong(0);

    /**
     * Registers profile service metrics.
     *
     * @param registry Micrometer registry
     * @param replicationLagService lag service
     */
    public ProfileMetrics(MeterRegistry registry, ReplicationLagService replicationLagService) {
        this.replicationLagService = replicationLagService;
        Gauge.builder("profile_replication_lag_seconds", lagSeconds, AtomicLong::get)
                .description("Regional profile read-model lag in seconds")
                .register(registry);
    }

    /**
     * Periodically refreshes the lag gauge from the read-model timestamp.
     */
    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:1000}")
    public void refreshReplicationLagMetric() {
        replicationLagService.getLag()
                .map(response -> response.lagSeconds() == null ? 0 : response.lagSeconds())
                .doOnNext(lagSeconds::set)
                .subscribe();
    }
}
