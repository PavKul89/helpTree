package org.example.helptreeservice.service;

import org.example.helptreeservice.config.CommentProperties;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommentRateLimiterTest {

    @Test
    void shouldAllowConfiguredNumberOfCommentsAndThenReject() {
        CommentProperties properties = new CommentProperties();
        properties.setRateLimitEnabled(true);
        properties.setPerMinute(2);
        CommentRateLimiter limiter = new CommentRateLimiter(properties);

        assertDoesNotThrow(() -> limiter.checkAllowed(1L));
        assertDoesNotThrow(() -> limiter.checkAllowed(1L));
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed(1L));
    }

    @Test
    void shouldKeepSeparateWindowsPerUser() {
        CommentProperties properties = new CommentProperties();
        properties.setRateLimitEnabled(true);
        properties.setPerMinute(1);
        CommentRateLimiter limiter = new CommentRateLimiter(properties);

        assertDoesNotThrow(() -> limiter.checkAllowed(1L));
        assertDoesNotThrow(() -> limiter.checkAllowed(2L));
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAllowed(1L));
    }

    @Test
    void shouldBeNoOpWhenDisabled() {
        CommentProperties properties = new CommentProperties();
        properties.setRateLimitEnabled(false);
        properties.setPerMinute(1);
        CommentRateLimiter limiter = new CommentRateLimiter(properties);

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.checkAllowed(1L));
        }
    }
}
