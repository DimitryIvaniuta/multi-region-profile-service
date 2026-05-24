package com.github.dimitryivaniuta.profile.api;

import com.github.dimitryivaniuta.profile.service.ProfileOpsService;
import com.github.dimitryivaniuta.profile.service.ProfileOutboxPublisher;
import com.github.dimitryivaniuta.profile.service.ReplicationLagService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Internal operational API for SRE/runbook workflows.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalOpsController {

    private final ReplicationLagService replicationLagService;
    private final ProfileOutboxPublisher outboxPublisher;
    private final ProfileOpsService profileOpsService;

    /**
     * Returns regional replication lag based on projection watermarks.
     *
     * @return replication lag response
     */
    @GetMapping("/replication/lag")
    public Mono<ReplicationLagResponse> getLag() {
        return replicationLagService.getLag();
    }

    /**
     * Returns the last consumed Kafka offset per projection partition.
     *
     * @return projection watermark responses
     */
    @GetMapping("/projection/watermarks")
    public Mono<List<ProjectionWatermarkResponse>> getWatermarks() {
        return profileOpsService.getProjectionWatermarks();
    }

    /**
     * Returns recent malformed events quarantined by the projection consumer.
     *
     * @param limit maximum number of invalid events to return
     * @return invalid event responses
     */
    @GetMapping("/projection/invalid-events")
    public Mono<List<InvalidProfileEventResponse>> getInvalidEvents(@RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return profileOpsService.getInvalidEvents(safeLimit);
    }

    /**
     * Returns transactional outbox counters grouped by status.
     *
     * @return outbox status counters
     */
    @GetMapping("/outbox/stats")
    public Mono<OutboxStatsResponse> getOutboxStats() {
        return profileOpsService.getOutboxStats();
    }

    /**
     * Manually submits a bounded batch of outbox rows to Kafka.
     *
     * @param limit requested upper bound for the replay batch
     * @return replay response
     */
    @PostMapping("/outbox/replay")
    public Mono<OutboxReplayResponse> replayOutbox(@RequestParam(defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return outboxPublisher.publishBatch(safeLimit)
                .map(submitted -> new OutboxReplayResponse(safeLimit, submitted));
    }
}
