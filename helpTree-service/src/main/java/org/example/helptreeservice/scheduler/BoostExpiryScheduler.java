package org.example.helptreeservice.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.service.PostService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes expired boost markers. Sorting and the public "boosted" flag already
 * treat expired boosts as inactive; this cleanup keeps stored state honest too.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoostExpiryScheduler {

    private final PostService postService;

    @Scheduled(cron = "${app.economy.boost-cleanup-cron:0 15 * * * *}")
    @SchedulerLock(name = "BoostExpiryScheduler_clearExpiredBoosts",
            lockAtMostFor = "PT55M", lockAtLeastFor = "PT1M")
    public void clearExpiredBoosts() {
        try {
            postService.clearExpiredBoosts();
        } catch (Exception e) {
            log.error("Ошибка при снятии истёкших boost: {}", e.getMessage(), e);
        }
    }
}
