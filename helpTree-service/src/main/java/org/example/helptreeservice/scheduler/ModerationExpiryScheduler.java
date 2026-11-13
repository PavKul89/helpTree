package org.example.helptreeservice.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.service.ModerationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Restores content whose temporary {@code HIDE_CONTENT} action has expired.
 * A permanent removal is never restored: the service only touches a target that
 * is still {@code HIDDEN}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationExpiryScheduler {

    private final ModerationService moderationService;

    @Scheduled(cron = "${app.moderation.expiry-cron:0 5 * * * *}")
    @SchedulerLock(name = "ModerationExpiryScheduler_restoreExpiredHiddenContent",
            lockAtMostFor = "PT55M", lockAtLeastFor = "PT1M")
    public void restoreExpiredHiddenContent() {
        try {
            int restored = moderationService.restoreExpiredHiddenContent();
            if (restored > 0) {
                log.info("Восстановлено объектов после истечения временного скрытия: {}", restored);
            }
        } catch (RuntimeException ex) {
            log.error("Ошибка при восстановлении объектов после временного скрытия", ex);
        }
    }
}
