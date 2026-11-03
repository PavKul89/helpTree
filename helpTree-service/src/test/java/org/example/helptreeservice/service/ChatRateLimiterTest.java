package org.example.helptreeservice.service;

import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatRateLimiterTest {

    private ChatProperties properties;
    private ChatRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new ChatProperties();
        properties.setRateLimitEnabled(true);
        properties.setMessagesPerMinute(2);
        limiter = new ChatRateLimiter(properties);
    }

    @Test
    void checkAllowed_shouldRejectAboveConfiguredLimit() {
        limiter.checkAllowed(1L);
        limiter.checkAllowed(1L);

        RateLimitExceededException error = assertThrows(
                RateLimitExceededException.class, () -> limiter.checkAllowed(1L));

        assertEquals(60, error.getRetryAfterSeconds());
    }

    @Test
    void checkAllowed_shouldTrackUsersIndependently() {
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
    }

    @Test
    void reset_shouldAllowUserAgain() {
        limiter.checkAllowed(1L);
        limiter.checkAllowed(1L);

        limiter.reset(1L);

        assertDoesNotThrow(() -> limiter.checkAllowed(1L));
    }
}
