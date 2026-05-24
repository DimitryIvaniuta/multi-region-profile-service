package com.github.dimitryivaniuta.profile.security;

import com.github.dimitryivaniuta.profile.config.RateLimitProperties;
import com.github.dimitryivaniuta.profile.config.RegionProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Redis-backed fixed-window rate limiter for public and internal API paths.
 *
 * <p>The limiter is intentionally fail-open on Redis errors. For this read-heavy profile service,
 * temporary availability is preferred over rejecting all traffic due to a cache-side outage. The
 * service still exposes Redis failures through logs and infrastructure metrics.</p>
 */
@Slf4j
@Component
@Order(-80)
@RequiredArgsConstructor
public class RedisRateLimitWebFilter implements WebFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;
    private final RegionProperties regionProperties;

    /**
     * Applies fixed-window request limits to API and internal endpoints.
     *
     * @param exchange current request and response exchange
     * @param chain next filter chain element
     * @return completion signal
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!rateLimitProperties.enabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!(path.startsWith("/api/") || path.startsWith("/internal/"))) {
            return chain.filter(exchange);
        }

        LimitPlan plan = planFor(exchange, path);
        if (plan.limit() <= 0) {
            return reject(exchange, plan);
        }
        String key = keyFor(exchange, plan.bucket());
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> refreshTtlIfFirstHit(key, count).then(enforce(exchange, chain, plan, count)))
                .onErrorResume(error -> {
                    log.warn("Rate limiter failed open path={} error={}", path, error.getMessage());
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> refreshTtlIfFirstHit(String key, Long count) {
        if (count != null && count == 1L) {
            return redisTemplate.expire(key, rateLimitProperties.window()).then();
        }
        return Mono.empty();
    }

    private Mono<Void> enforce(ServerWebExchange exchange, WebFilterChain chain, LimitPlan plan, Long count) {
        long currentCount = count == null ? 0 : count;
        exchange.getResponse().getHeaders().set("X-RateLimit-Limit", Long.toString(plan.limit()));
        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", Long.toString(Math.max(0, plan.limit() - currentCount)));
        if (currentCount > plan.limit()) {
            return reject(exchange, plan);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange, LimitPlan plan) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", Long.toString(rateLimitProperties.window().toSeconds()));
        byte[] body = ("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests for " + plan.bucket() + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private LimitPlan planFor(ServerWebExchange exchange, String path) {
        if (path.startsWith("/internal/")) {
            return new LimitPlan("internal", rateLimitProperties.internalLimit());
        }
        HttpMethod method = exchange.getRequest().getMethod();
        if (HttpMethod.GET.equals(method)) {
            return new LimitPlan("read", rateLimitProperties.readLimit());
        }
        return new LimitPlan("write", rateLimitProperties.writeLimit());
    }

    private String keyFor(ServerWebExchange exchange, String bucket) {
        Duration window = rateLimitProperties.window();
        long windowSeconds = Math.max(1, window.toSeconds());
        long slot = Instant.now().getEpochSecond() / windowSeconds;
        return "rate-limit:v1:" + regionProperties.name() + ":" + bucket + ":" + principalHash(exchange) + ":" + slot;
    }

    private String principalHash(ServerWebExchange exchange) {
        String principal = exchange.getRequest().getHeaders().getFirst(INTERNAL_KEY_HEADER);
        if (principal == null || principal.isBlank()) {
            principal = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        }
        if (principal == null || principal.isBlank()) {
            principal = String.valueOf(exchange.getRequest().getRemoteAddress());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(principal.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record LimitPlan(String bucket, long limit) {
    }
}
