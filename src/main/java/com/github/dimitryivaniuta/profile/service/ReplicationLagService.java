package com.github.dimitryivaniuta.profile.service;

import com.github.dimitryivaniuta.profile.api.ReplicationLagResponse;
import com.github.dimitryivaniuta.profile.config.ConsistencyProperties;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import com.github.dimitryivaniuta.profile.persistence.ProfileStore;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Calculates regional read-model replication lag for operational checks and alerts.
 */
@Service
@RequiredArgsConstructor
public class ReplicationLagService {

    private final ProfileStore profileStore;
    private final RegionProperties regionProperties;
    private final ConsistencyProperties consistencyProperties;
    private final ConsistencySlaEvaluator slaEvaluator;

    /**
     * Computes lag from projection watermarks, falling back to the read model for older deployments.
     *
     * @return replication lag response
     */
    public Mono<ReplicationLagResponse> getLag() {
        Instant measuredAt = Instant.now();
        return profileStore.findLatestWatermarkEventTime()
                .map(latestEventTime -> response(latestEventTime, measuredAt, "WATERMARK"))
                .switchIfEmpty(Mono.defer(() -> profileStore.findLatestProjectedEventTime()
                        .map(latestEventTime -> response(latestEventTime, measuredAt, "READ_MODEL"))))
                .switchIfEmpty(Mono.just(emptyResponse(measuredAt)));
    }

    private ReplicationLagResponse response(Instant latestEventTime, Instant measuredAt, String source) {
        long lag = slaEvaluator.lagSeconds(latestEventTime, measuredAt);
        long slaSeconds = consistencyProperties.visibilitySla().toSeconds();
        return new ReplicationLagResponse(
                regionProperties.name(),
                regionProperties.role().name(),
                latestEventTime,
                measuredAt,
                lag,
                slaSeconds,
                lag <= slaSeconds,
                source
        );
    }

    private ReplicationLagResponse emptyResponse(Instant measuredAt) {
        return new ReplicationLagResponse(
                regionProperties.name(),
                regionProperties.role().name(),
                null,
                measuredAt,
                null,
                consistencyProperties.visibilitySla().toSeconds(),
                true,
                "EMPTY"
        );
    }
}
