package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.CommentProperties;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.example.helptreeservice.service.ratelimit.DistributedRateLimiter;
import org.example.helptreeservice.service.ratelimit.RateLimitDecision;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Per-user comment limiter backed by the shared counter store. The counter is
 * incremented in its own transaction, so rolling back the comment insert does
 * not give the caller an unlimited retry budget.
 */
@Component
@RequiredArgsConstructor
public class CommentRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final CommentProperties properties;
    private final DistributedRateLimiter rateLimiter;

    public void checkAllowed(Long userId) {
        if (!properties.isRateLimitEnabled() || userId == null) {
            return;
        }

        RateLimitDecision decision = rateLimiter.acquire(
                "comment:user:" + userId,
                Math.max(1, properties.getPerMinute()),
                WINDOW);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(
                    "Слишком много комментариев. Попробуйте позже.",
                    decision.retryAfterSeconds());
        }
    }

    public void reset(Long userId) {
        if (userId != null) {
            rateLimiter.reset("comment:user:" + userId);
        }
    }
}
