package com.example.gatewayservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimitService {

    private static final long WINDOW_MS = 60_000;
    private static final long CLEANUP_THRESHOLD_MS = 120_000;

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    public record BucketEntry(AtomicInteger counter, long windowStart) {}

    public boolean tryConsume(String key, int limit) {
        long now = Instant.now().toEpochMilli();

        BucketEntry entry = buckets.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart() > WINDOW_MS) {
                AtomicInteger newCounter = new AtomicInteger(0);
                return new BucketEntry(newCounter, now);
            }
            return existing;
        });

        int currentCount = entry.counter().incrementAndGet();
        return currentCount <= limit;
    }

    @Scheduled(fixedRate = 60_000)
    public void resetExpiredBuckets() {
        long now = Instant.now().toEpochMilli();
        int resetCount = 0;

        for (Map.Entry<String, BucketEntry> entry : buckets.entrySet()) {
            BucketEntry bucket = entry.getValue();
            if (now - bucket.windowStart() > WINDOW_MS) {
                bucket.counter().set(0);
                resetCount++;
            }
        }

        if (resetCount > 0) {
            log.debug("Сброс счётчиков rate limit: сброшено {} просроченных счётчиков", resetCount);
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredBuckets() {
        long now = Instant.now().toEpochMilli();
        long expiredThreshold = now - CLEANUP_THRESHOLD_MS;

        int before = buckets.size();
        buckets.entrySet().removeIf(e -> e.getValue().windowStart() < expiredThreshold);
        int removed = before - buckets.size();

        if (removed > 0) {
            log.info("Очистка rate limit: удалено {} просроченных записей, осталось: {}", removed, buckets.size());
        }
    }

    public int getActiveBucketsCount() {
        return buckets.size();
    }
}
