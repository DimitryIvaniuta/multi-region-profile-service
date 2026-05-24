package com.github.dimitryivaniuta.profile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lightweight API-key settings for local and service-to-service protection.
 *
 * <p>In production this can be replaced by OAuth2/OIDC at the gateway without changing domain
 * code. The filter is still useful for direct internal deployments and local testing.</p>
 *
 * @param apiKey key required for public profile API calls
 * @param internalKey key required for internal operational endpoints
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String apiKey, String internalKey) {
}
