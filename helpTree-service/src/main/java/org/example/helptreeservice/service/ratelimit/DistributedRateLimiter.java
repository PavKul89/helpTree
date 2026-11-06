package org.example.helptreeservice.service.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Application-facing entry point for the shared counter store. Keys are
 * normalized here so the filter, chat and comment limiters all use the same
 * bounded format.
 */
@Service
@RequiredArgsConstructor
public class DistributedRateLimiter {

    private final RateLimitStore store;

    public RateLimitDecision acquire(String key, int limit, Duration window) {
        long seconds = Math.max(1, window.toSeconds());
        return store.increment(RateLimitKeys.normalize(key), Math.max(1, limit), seconds);
    }

    public void reset(String key) {
        store.delete(RateLimitKeys.normalize(key));
    }
}
