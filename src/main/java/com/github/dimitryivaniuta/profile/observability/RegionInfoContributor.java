package com.github.dimitryivaniuta.profile.observability;

import com.github.dimitryivaniuta.profile.config.ConsistencyProperties;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Adds region and consistency information to the Actuator info endpoint.
 */
@Component
@RequiredArgsConstructor
public class RegionInfoContributor implements InfoContributor {

    private final RegionProperties regionProperties;
    private final ConsistencyProperties consistencyProperties;

    /**
     * Contributes profile-service runtime metadata.
     *
     * @param builder actuator info builder
     */
    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("region", regionProperties.name())
                .withDetail("role", regionProperties.role().name())
                .withDetail("visibilitySlaSeconds", consistencyProperties.visibilitySla().toSeconds())
                .withDetail("cacheTtlSeconds", consistencyProperties.cacheTtl().toSeconds());
    }
}
