package com.github.dimitryivaniuta.profile.observability;

import com.github.dimitryivaniuta.profile.service.ReplicationLagService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reactive health indicator that marks the regional projection degraded when lag exceeds the SLA.
 */
@Component("replicationSla")
@RequiredArgsConstructor
public class ReplicationSlaHealthIndicator implements ReactiveHealthIndicator {

    private final ReplicationLagService replicationLagService;

    /**
     * Evaluates the current regional replication-lag SLA.
     *
     * @return health result containing lag details
     */
    @Override
    public Mono<Health> health() {
        return replicationLagService.getLag()
                .map(response -> {
                    Health.Builder builder = response.withinSla() ? Health.up() : Health.status("DEGRADED");
                    return builder
                            .withDetail("region", response.region())
                            .withDetail("role", response.role())
                            .withDetail("lagSeconds", response.lagSeconds())
                            .withDetail("visibilitySlaSeconds", response.visibilitySlaSeconds())
                            .withDetail("lagSource", response.lagSource())
                            .build();
                })
                .onErrorResume(error -> Mono.just(Health.down(error).build()));
    }
}
