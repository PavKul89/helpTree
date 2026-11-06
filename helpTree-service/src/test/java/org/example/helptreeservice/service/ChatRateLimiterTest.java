package org.example.helptreeservice.service;

import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.example.helptreeservice.service.ratelimit.DistributedRateLimiter;
import org.example.helptreeservice.service.ratelimit.RateLimitDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatRateLimiterTest {

    private ChatProperties properties;
    private DistributedRateLimiter rateLimiter;
    private ChatRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new ChatProperties();
        properties.setRateLimitEnabled(true);
        properties.setMessagesPerMinute(2);
        rateLimiter = mock(DistributedRateLimiter.class);
        limiter = new ChatRateLimiter(properties, rateLimiter);
    }

    @Test
    void checkAllowed_shouldRejectAboveConfiguredLimit() {
        when(rateLimiter.acquire(eq("chat:user:1"), eq(2), any(Duration.class)))
                .thenReturn(RateLimitDecision.allowed(2, 1, 60),
                        RateLimitDecision.allowed(2, 0, 60),
                        RateLimitDecision.rejected(2, 42));

        limiter.checkAllowed(1L);
        limiter.checkAllowed(1L);

        RateLimitExceededException error = assertThrows(
                RateLimitExceededException.class, () -> limiter.checkAllowed(1L));

        assertEquals(42, error.getRetryAfterSeconds());
    }

    @Test
    void checkAllowed_shouldTrackUsersIndependently() {
        when(rateLimiter.acquire(eq("chat:user:1"), eq(2), any(Duration.class)))
                .thenReturn(RateLimitDecision.allowed(2, 1, 60),
                        RateLimitDecision.allowed(2, 0, 60),
                        RateLimitDecision.rejected(2, 60));
        when(rateLimiter.acquire(eq("chat:user:2"), eq(2), any(Duration.class)))
                .thenReturn(RateLimitDecision.allowed(2, 1, 60));

        limiter.checkAllowed(1L);
        limiter.checkAllowed(1L);

        assertDoesNotThrow(() -> limiter.checkAllowed(2L));
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed(1L));
    }

    @Test
    void checkAllowed_shouldBeNoOpWhenDisabled() {
        properties.setRateLimitEnabled(false);

        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100; i++) {
                limiter.checkAllowed(1L);
            }
        });
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void reset_shouldRemoveTheSharedCounter() {
        limiter.reset(1L);

        verify(rateLimiter).reset("chat:user:1");
    }
}
