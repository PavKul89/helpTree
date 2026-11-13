package org.example.helptreeservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.NotificationProperties;
import org.example.helptreeservice.repository.OutboxEventRepository;
import org.example.helptreeservice.service.notification.OutboxDispatcher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Durable notification delivery sweep. The scheduler is a safety net: most
 * events are dispatched immediately after commit, while this tick retries
 * FAILED events, releases abandoned PROCESSING claims and catches up after a
 * restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxScheduler {

    private final OutboxDispatcher outboxDispatcher;
    private final OutboxEventRepository outboxEventRepository;
    private final NotificationProperties notificationProperties;

    @Scheduled(cron = "${app.notifications.dispatch-cron:0 * * * * *}")
    public void dispatchNotifications() {
        try {
            outboxDispatcher.dispatchDue();
        } catch (RuntimeException e) {
            log.error("Ошибка при обработке outbox уведомлений", e);
        }
    }

    @Scheduled(cron = "${app.notifications.retention-cron:0 15 3 * * *}")
    public void purgePublishedEvents() {
        int retentionDays = notificationProperties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int batchSize = Math.max(1, notificationProperties.getRetentionBatchSize());
        int maxBatches = Math.max(1, notificationProperties.getRetentionMaxBatchesPerRun());
        int total = 0;
        try {
            for (int batch = 0; batch < maxBatches; batch++) {
                int deleted = outboxEventRepository.deletePublishedBefore(cutoff, batchSize);
                total += deleted;
                if (deleted < batchSize) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.error("Ошибка при очистке опубликованных outbox-событий старше {}", cutoff, e);
            return;
        }
        if (total > 0) {
            log.info("Удалено {} опубликованных outbox-событий старше {}", total, cutoff);
        }
    }
}
