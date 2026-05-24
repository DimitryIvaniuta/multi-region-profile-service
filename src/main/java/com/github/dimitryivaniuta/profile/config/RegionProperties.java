package com.github.dimitryivaniuta.profile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Region identity and role used to gate write endpoints, event consumption and metrics tags.
 *
 * @param name logical region name, for example eu-central-1 or us-east-1
 * @param role deployment role of this service instance
 */
@ConfigurationProperties(prefix = "app.region")
public record RegionProperties(String name, RegionRole role) {

    /**
     * Returns true when this instance is allowed to accept profile mutations.
     *
     * @return true for the primary region only
     */
    public boolean isPrimary() {
        return role == RegionRole.PRIMARY;
    }
}
