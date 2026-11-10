package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts the public profile projection at the right moment.
 *
 * <p>Evicting before commit lets a concurrent reader reload and cache the old
 * row again. The eviction therefore runs after the surrounding transaction
 * commits; outside a transaction it runs immediately.</p>
 */
@Service
@RequiredArgsConstructor
public class PublicProfileCache {

    public static final String CACHE_NAME = "publicUserProfile";
    public static final String PRIVACY_CACHE_NAME = "publicPrivacySettings";

    private final CacheManager cacheManager;

    public void evictAfterCommit(Long userId) {
        runAfterCommit(() -> {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.evict(userId);
            }
            Cache privacy = cacheManager.getCache(PRIVACY_CACHE_NAME);
            if (privacy != null) {
                privacy.evict(userId);
            }
        });
    }

    public void evictAllAfterCommit() {
        runAfterCommit(() -> {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.clear();
            }
            Cache privacy = cacheManager.getCache(PRIVACY_CACHE_NAME);
            if (privacy != null) {
                privacy.clear();
            }
        });
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
