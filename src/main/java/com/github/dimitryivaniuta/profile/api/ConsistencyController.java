package com.github.dimitryivaniuta.profile.api;

import com.github.dimitryivaniuta.profile.config.ConsistencyProperties;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint that documents the current consistency contract.
 */
@RestController
@RequestMapping("/api/v1/consistency")
@RequiredArgsConstructor
public class ConsistencyController {

    private final RegionProperties regionProperties;
    private final ConsistencyProperties consistencyProperties;

    /**
     * Returns the configured visibility SLA for this region.
     *
     * @return consistency SLA response
     */
    @GetMapping("/sla")
    public ConsistencySlaResponse getSla() {
        return new ConsistencySlaResponse(
                regionProperties.name(),
                regionProperties.role().name(),
                consistencyProperties.visibilitySla().toSeconds(),
                consistencyProperties.cacheTtl().toSeconds(),
                "EVENTUAL_CONSISTENCY"
        );
    }
}
