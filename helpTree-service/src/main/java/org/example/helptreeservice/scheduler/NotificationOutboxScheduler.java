package org.example.helptreeservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.service.notification.OutboxDispatcher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Scheduled(cron = "${app.notifications.dispatch-cron:0 * * * * *}")
    public void dispatchNotifications() {
        try {
            outboxDispatcher.dispatchDue();
        } catch (RuntimeException e) {
            log.error("Ошибка при обработке outbox уведомлений", e);
        }
    }
}
