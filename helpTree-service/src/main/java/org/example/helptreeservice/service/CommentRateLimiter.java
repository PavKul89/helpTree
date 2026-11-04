package org.example.helptreeservice.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.example.helptreeservice.config.CommentProperties;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user comment limiter. It is intentionally in-memory for the current
 * single-instance deployment; a horizontally scaled deployment should replace
 * it with a shared limiter (Redis/bucket4j), like ChatRateLimiter.
 */
@Component
public class CommentRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final CommentProperties properties;
    private final Map<Long, RateLimiter> limiters = new ConcurrentHashMap<>();

    public CommentRateLimiter(CommentProperties properties) {
        this.properties = properties;
    }

    public void checkAllowed(Long userId) {
        if (!properties.isRateLimitEnabled() || userId == null) {
            return;
        }

        RateLimiter limiter = limiters.computeIfAbsent(userId, this::createLimiter);
        if (!limiter.acquirePermission()) {
            throw new RateLimitExceededException(
                    "Слишком много комментариев. Попробуйте позже.",
                    Math.max(1, WINDOW.getSeconds()));
        }
    }

    public void reset(Long userId) {
        if (userId != null) {
            limiters.remove(userId);
        }
    }

    private RateLimiter createLimiter(Long userId) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(Math.max(1, properties.getPerMinute()))
                .limitRefreshPeriod(WINDOW)
                .timeoutDuration(Duration.ZERO)
                .build();
        return RateLimiter.of("comment-user-" + userId, config);
    }
}
