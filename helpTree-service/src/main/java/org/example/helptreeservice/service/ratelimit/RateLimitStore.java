package org.example.helptreeservice.service.ratelimit;

import java.util.Optional;

/**
 * Shared state for rate limiting and login lockouts. Implementations must be
 * safe to call concurrently from multiple application instances.
 */
public interface RateLimitStore {

    /**
     * Atomically increments the fixed-window counter and resets it when the
     * previous window has expired.
     */
    RateLimitDecision increment(String key, int limit, long windowSeconds);

    /** Removes a counter (used after a successful login and by tests). */
    void delete(String key);

    /** Removes a lock immediately (used after a successful login). */
    void deleteLock(String key);

    /** Returns the remaining lockout in seconds, or empty when not locked. */
    Optional<Long> findActiveLock(String key);

    /** Creates or extends a lock. Expired locks are overwritten automatically. */
    void acquireLock(String key, long durationSeconds);

    /** Deletes at most {@code batchSize} expired counters. */
    int deleteExpiredBuckets(int batchSize);

    /** Deletes at most {@code batchSize} expired locks. */
    int deleteExpiredLocks(int batchSize);
}
