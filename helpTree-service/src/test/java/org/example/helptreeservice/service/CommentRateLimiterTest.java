package org.example.helptreeservice.service;

import org.example.helptreeservice.config.CommentProperties;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.example.helptreeservice.service.ratelimit.DistributedRateLimiter;
import org.example.helptreeservice.service.ratelimit.RateLimitDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommentRateLimiterTest {

    @Test
    void shouldAllowConfiguredNumberOfCommentsAndThenReject() {
        CommentProperties properties = new CommentProperties();
        properties.setRateLimitEnabled(true);
        properties.setPerMinute(2);
        DistributedRateLimiter rateLimiter = mock(DistributedRateLimiter.class);
        when(rateLimiter.acquire(eq("comment:user:1"), eq(2), any(Duration.class)))
                .thenReturn(RateLimitDecision.allowed(2, 1, 60),
                        RateLimitDecision.allowed(2, 0, 60),
                        RateLimitDecision.rejected(2, 60));
        CommentRateLimiter limiter = new CommentRateLimiter(properties, rateLimiter);

        assertDoesNotThrow(() -> limiter.checkAllowed(1L));
        assertDoesNotThrow(() -> limiter.checkAllowed(1L));
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed(1L));
    }

    @Test
    void shouldKeepSeparateWindowsPerUser() {
        CommentProperties properties = new CommentProperties();
        properties.setRateLimitEnabled(true);
        properties.setPerMinute(1);
        DistributedRateLimiter rateLimiter = mock(DistributedRateLimiter.class);
        when(rateLimiter.acquire(eq("comment:user:1"), eq(1), any(Duration.class)))
                .thenReturn(RateLimitDecision.allowed(1, 0, 60),
                        RateLimitDecision.rejected(1, 60));
        when(rateLimiter.acquire(eq("comment:user:2"), eq(1), any(Duration.class)))
                .thenReturn(RateLimitDecision.allowed(1, 0, 60));
        CommentRateLimiter limiter = new CommentRateLimiter(properties, rateLimiter);

        assertDoesNotThrow(() -> limiter.checkAllowed(1L));
        assertDoesNotThrow(() -> limiter.checkAllowed(2L));
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed(1L));
    }

    @Test
    void shouldBeNoOpWhenDisabled() {
        CommentProperties properties = new CommentProperties();
        properties.setRateLimitEnabled(false);
        properties.setPerMinute(1);
        DistributedRateLimiter rateLimiter = mock(DistributedRateLimiter.class);
        CommentRateLimiter limiter = new CommentRateLimiter(properties, rateLimiter);

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.checkAllowed(1L));
        }
        verifyNoInteractions(rateLimiter);
    }
}
