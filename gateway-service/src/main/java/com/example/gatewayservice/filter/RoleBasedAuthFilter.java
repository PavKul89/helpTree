package com.example.gatewayservice.filter;

import com.example.gatewayservice.config.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RoleBasedAuthFilter implements GlobalFilter {

    private final JwtUtils jwtUtils;

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/reviews/help",
            "/api/reviews/user",
            "/api/ratings/",
            "/api/posts/*/comments",
            "/api/posts/*/comments/",
            "/actuator/health"
    );

    private static final Set<String> ACTUATOR_PATHS = Set.of(
            "/actuator/"
    );

    private static final Set<String> ADMIN_ONLY_PATHS = Set.of(
            "/api/users"
    );

    private static final Set<String> USER_PATH_EXCEPTIONS = Set.of(
            "/api/users/me",
            "/api/users/telegram",
            "/api/users/current",
            "/api/reviews"
    );

    private static final Set<String> USER_ID_PATH_EXCEPTIONS = Set.of(
            "/telegram",
            "/reviews"
    );

    private static final Set<HttpMethod> ADMIN_ONLY_METHODS = Set.of(
            HttpMethod.POST,
            HttpMethod.DELETE
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        log.debug("RBAC: {} {}", method, path);

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        if (path.startsWith("/internal/")) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("RBAC: Missing token for {} {}", method, path);
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtUtils.isTokenValid(token)) {
                log.warn("RBAC: Invalid token for {} {}", method, path);
                return unauthorized(exchange);
            }

            String role = jwtUtils.extractRole(token);
            Long userId = jwtUtils.extractUserId(token);
            String email = jwtUtils.extractEmail(token);

            if (isAdminOnlyPath(path) && ADMIN_ONLY_METHODS.contains(method)) {
                if (!"ADMIN".equals(role)) {
                    log.warn("RBAC: Non-admin {} tried {} on admin path: {}", userId, method, path);
                    return forbidden(exchange, "Только администратор имеет доступ");
                }
            }

            log.info("RBAC: userId={}, role={}, {} {}", userId, role, method, path);

            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId.toString())
                    .header("X-User-Role", role != null ? role : "USER")
                    .header("X-User-Email", email)
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            log.error("RBAC: Error: {}", e.getMessage());
            return unauthorized(exchange);
        }
    }

    private boolean isPublicPath(String path) {
        if (path.startsWith("/api/auth/")) {
            return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
        }
        if (path.startsWith("/actuator/")) {
            return ACTUATOR_PATHS.stream().anyMatch(path::startsWith);
        }
        return false;
    }

    private boolean isAdminOnlyPath(String path) {
        if ("/api/users".equals(path)) {
            return true;
        }
        if (USER_PATH_EXCEPTIONS.stream().anyMatch(path::startsWith)) {
            return false;
        }
        if (path.matches("/api/users/\\d+")) {
            return false;
        }
        if (path.matches("/api/users/\\d+/avatar")) {
            return false;
        }
        if (path.matches("/api/users/\\d+.*")) {
            for (String exception : USER_ID_PATH_EXCEPTIONS) {
                if (path.endsWith(exception)) {
                    return false;
                }
            }
        }
        return ADMIN_ONLY_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Требуется авторизация\"}";
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes()))
        );
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = String.format("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"%s\"}", message);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes()))
        );
    }
}