package com.example.gatewayservice.filter;

import com.example.gatewayservice.config.RateLimitConfig;
import lombok.RequiredArgsConstructor;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimitConfig config;
    private final RateLimitService rateLimitService;

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
        String clientIp = getClientIp(exchange.getRequest());

        String bucketType = getBucketType(path, method);
        int limit = getLimit(bucketType);
        String key = clientIp + ":" + bucketType;

        if (rateLimitService.tryConsume(key, limit)) {
            log.debug("Rate limit OK: IP={}, path={}, type={}", clientIp, path, bucketType);
            return chain.filter(exchange);
        } else {
            log.warn("Превышен лимит запросов: IP={}, path={}, type={}, limit={}", clientIp, path, bucketType, limit);

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
    }

    private String getClientIp(org.springframework.http.server.reactive.ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }

        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
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
