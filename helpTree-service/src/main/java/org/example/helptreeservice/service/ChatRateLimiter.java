package org.example.helptreeservice.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user send limiter. It is intentionally in-memory: the target deployment is
 * a single application instance, and the limiter only protects against message
 * flooding. When the STOMP relay is enabled for horizontal scaling, a shared
 * limiter (Redis/bucket4j) should replace this implementation.
 */
@Component
public class ChatRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ChatProperties chatProperties;
    private final Map<Long, RateLimiter> limiters = new ConcurrentHashMap<>();

    public ChatRateLimiter(ChatProperties chatProperties) {
        this.chatProperties = chatProperties;
    }

    public void checkAllowed(Long userId) {
        if (!chatProperties.isRateLimitEnabled() || userId == null) {
            return;
        }

        RateLimiter limiter = limiters.computeIfAbsent(userId, this::createLimiter);
        if (!limiter.acquirePermission()) {
            throw new RateLimitExceededException(
                    "Слишком много сообщений. Попробуйте позже.",
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
                .limitForPeriod(Math.max(1, chatProperties.getMessagesPerMinute()))
                .limitRefreshPeriod(WINDOW)
                .timeoutDuration(Duration.ZERO)
                .build();
        return RateLimiter.of("chat-user-" + userId, config);
    }
}
