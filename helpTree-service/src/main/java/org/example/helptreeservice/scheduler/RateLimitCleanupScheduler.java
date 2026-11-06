package org.example.helptreeservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.RateLimitConfig;
import org.example.helptreeservice.service.ratelimit.RateLimitStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes expired counters and lockouts in bounded batches. Rows are already
 * logically expired for the lookup queries; this job only reclaims space so the
 * two tables cannot grow without limit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitCleanupScheduler {

    private static final int MAX_BATCHES_PER_RUN = 10;

    private final RateLimitStore store;
    private final RateLimitConfig config;

    @Scheduled(cron = "${app.rate-limit.cleanup-cron:0 */5 * * * *}")
    public void cleanup() {
        if (!config.isEnabled()) {
            return;
        }
        int batchSize = Math.max(1, config.getCleanupBatchSize());
        int buckets = drain(() -> store.deleteExpiredBuckets(batchSize), batchSize);
        int locks = drain(() -> store.deleteExpiredLocks(batchSize), batchSize);
        if (buckets > 0 || locks > 0) {
            log.debug("Rate-limit cleanup removed {} counter(s) and {} lock(s)", buckets, locks);
        }
    }

    private int drain(BatchDelete delete, int batchSize) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int deleted = delete.run();
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }

    @FunctionalInterface
    private interface BatchDelete {
        int run();
    }
}
