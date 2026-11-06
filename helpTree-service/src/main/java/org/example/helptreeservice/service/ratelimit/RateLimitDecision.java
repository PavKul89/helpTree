package org.example.helptreeservice.service.ratelimit;

/**
 * Result of one counter increment. {@code remaining} is never negative and
 * {@code retryAfterSeconds} is the number of seconds left in the current
 * window, so it can be sent verbatim in the Retry-After header.
 */
public record RateLimitDecision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {

    public static RateLimitDecision allowed(int limit, int remaining, long retryAfterSeconds) {
        return new RateLimitDecision(true, limit, remaining, retryAfterSeconds);
    }

    public static RateLimitDecision rejected(int limit, long retryAfterSeconds) {
        return new RateLimitDecision(false, limit, 0, retryAfterSeconds);
    }
}
