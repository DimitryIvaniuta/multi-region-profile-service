package com.github.dimitryivaniuta.profile.security;

import com.github.dimitryivaniuta.profile.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Minimal API-key filter for public and internal endpoints.
 *
 * <p>The filter avoids bringing a full identity provider into this reference service while still
 * preventing accidental unauthenticated writes or operational calls. A production edge can replace
 * this with OAuth2/JWT enforcement. API-key comparisons use constant-time byte comparison to avoid
 * trivial timing leaks.</p>
 */
@Component
@Order(-90)
@RequiredArgsConstructor
public class ApiKeyWebFilter implements WebFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    private final SecurityProperties securityProperties;

    /**
     * Validates request headers for protected API paths.
     *
     * @param exchange current request and response exchange
     * @param chain next web filter chain element
     * @return completion signal for the request
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (path.startsWith("/actuator/health") || path.startsWith("/actuator/info")) {
            return chain.filter(exchange);
        }
        if (path.startsWith("/internal/")) {
            return requireHeader(exchange, chain, INTERNAL_KEY_HEADER, securityProperties.internalKey());
        }
        if (path.startsWith("/api/")) {
            return requireHeader(exchange, chain, API_KEY_HEADER, securityProperties.apiKey());
        }
        return chain.filter(exchange);
    }

    private Mono<Void> requireHeader(ServerWebExchange exchange, WebFilterChain chain, String headerName, String expectedValue) {
        String actual = exchange.getRequest().getHeaders().getFirst(headerName);
        if (matches(expectedValue, actual)) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid " + headerName + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private boolean matches(String expectedValue, String actualValue) {
        if (expectedValue == null || actualValue == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedValue.getBytes(StandardCharsets.UTF_8),
                actualValue.getBytes(StandardCharsets.UTF_8)
        );
    }
}
