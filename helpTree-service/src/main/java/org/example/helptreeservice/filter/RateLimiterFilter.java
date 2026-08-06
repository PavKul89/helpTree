package org.example.helptreeservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.RateLimitConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private final RateLimitConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimiterFilter(RateLimitConfig config) {
        this.config = config;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        if (path.startsWith("/actuator") || path.startsWith("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String bucketType = getBucketType(path, method);
        int limit = getLimit(bucketType);

        RateLimiter rateLimiter = limiters.computeIfAbsent(bucketType, k ->
                createRateLimiter(bucketType, limit)
        );

        if (rateLimiter.acquirePermission()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Превышен лимит запросов: path={}, type={}, limit={}", path, bucketType, limit);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("Retry-After", String.valueOf(config.getWindowSeconds()));

        String body = objectMapper.writeValueAsString(Map.of(
                "status", 429,
                "error", "Too Many Requests",
                "message", "Превышен лимит запросов. Попробуйте через " + config.getWindowSeconds() + " секунд.",
                "path", path
        ));

        response.getWriter().write(body);
    }

    private RateLimiter createRateLimiter(String bucketType, int limit) {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(config.getWindowSeconds()))
                .limitForPeriod(limit)
                .timeoutDuration(Duration.ofSeconds(5))
                .build();

        return RateLimiter.of("helptree-" + bucketType, rateLimiterConfig);
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
}
