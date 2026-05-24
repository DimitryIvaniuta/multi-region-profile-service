package com.github.dimitryivaniuta.profile.security;

import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Ensures every request and response carries a correlation identifier for log tracing.
 */
@Component
@Order(-100)
public class CorrelationIdWebFilter implements WebFilter {

    /** Public HTTP header used for request correlation. */
    public static final String HEADER_NAME = "X-Correlation-Id";

    /**
     * Adds a generated correlation identifier when the caller did not provide one.
     *
     * @param exchange current request and response exchange
     * @param chain next web filter chain element
     * @return completion signal
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
        return chain.filter(exchange);
    }
}
