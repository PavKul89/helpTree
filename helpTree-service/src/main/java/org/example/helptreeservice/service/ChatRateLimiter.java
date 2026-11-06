package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.example.helptreeservice.service.ratelimit.DistributedRateLimiter;
import org.example.helptreeservice.service.ratelimit.RateLimitDecision;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-user send limiter backed by the shared counter store, so a user cannot
 * double their quota by connecting to another application instance.
 */
@Component
@RequiredArgsConstructor
public class ChatRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ChatProperties chatProperties;
    private final DistributedRateLimiter rateLimiter;

    public void checkAllowed(Long userId) {
        if (!chatProperties.isRateLimitEnabled() || userId == null) {
            return;
        }

        RateLimitDecision decision = rateLimiter.acquire(
                "chat:user:" + userId,
                Math.max(1, chatProperties.getMessagesPerMinute()),
                WINDOW);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(
                    "Слишком много сообщений. Попробуйте позже.",
                    decision.retryAfterSeconds());
        }
    }

    public void reset(Long userId) {
        if (userId != null) {
            rateLimiter.reset("chat:user:" + userId);
        }
    }
}
