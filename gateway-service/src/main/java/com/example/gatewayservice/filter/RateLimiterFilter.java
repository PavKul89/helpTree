package com.example.gatewayservice.filter;

import com.example.gatewayservice.config.RateLimitConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimiterFilter implements GlobalFilter, Ordered {

    private final RateLimitConfig config;
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimiterFilter(RateLimitConfig config) {
        this.config = config;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!config.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().toString();

        if (path.startsWith("/actuator") || path.startsWith("/error")) {
            return chain.filter(exchange);
        }

        String method = exchange.getRequest().getMethod().toString();
        String bucketType = getBucketType(path, method);
        int limit = getLimit(bucketType);

        RateLimiter rateLimiter = limiters.computeIfAbsent(bucketType, k ->
                createRateLimiter(bucketType, limit)
        );

        if (rateLimiter.acquirePermission()) {
            return chain.filter(exchange);
        }

        log.warn("Превышен лимит запросов: path={}, type={}, limit={}", path, bucketType, limit);

        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.getHeaders().add("X-RateLimit-Limit", String.valueOf(limit));
        response.getHeaders().add("X-RateLimit-Remaining", "0");
        response.getHeaders().add("Retry-After", String.valueOf(config.getWindowSeconds()));

        String body = """
                {"status":429,"error":"Too Many Requests","message":"Превышен лимит запросов. Попробуйте через %d секунд.","path":"%s"}
                """.formatted(config.getWindowSeconds(), path);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    private RateLimiter createRateLimiter(String bucketType, int limit) {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(config.getWindowSeconds()))
                .limitForPeriod(limit)
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        return RateLimiter.of("gateway-" + bucketType, rateLimiterConfig);
    }

    private String getBucketType(String path, String method) {
        if (path.contains("/api/auth/") || (path.contains("/api/users") && !path.contains("/public"))) {
            return "auth";
        } else if (("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))
                && (path.contains("/posts") || path.contains("/users"))) {
            return "create";
        }
        return "default";
    }

    private int getLimit(String bucketType) {
        return switch (bucketType) {
            case "auth" -> config.getAuthLimit();
            case "create" -> config.getCreateLimit();
            default -> config.getDefaultLimit();
        };
    }

    @Override
    public int getOrder() {
        return 90;
    }
}
