package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.ChatProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Physically removes messages older than {@code app.chat.retention-days}.
 *
 * <p>The sweep is disabled when the value is 0. Each batch is its own
 * transaction, so a large backlog never holds a long lock; the number of
 * batches per run is capped to keep a scheduled run short. Attachment rows are
 * released only after the last live message that references them is gone.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRetentionScheduler {

    private final ChatService chatService;
    private final ChatProperties chatProperties;

    @Scheduled(cron = "${app.chat.retention-cron:0 30 3 * * *}")
    public void purgeExpiredMessages() {
        if (chatProperties.getRetentionDays() <= 0) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now()
                .minusDays(chatProperties.getRetentionDays());
        int batchSize = Math.max(1, chatProperties.getRetentionBatchSize());
        int maxBatches = Math.max(1, chatProperties.getRetentionMaxBatchesPerRun());
        int total = 0;

        for (int batch = 0; batch < maxBatches; batch++) {
            int deleted;
            try {
                deleted = chatService.purgeExpiredMessages(cutoff, batchSize);
            } catch (Exception e) {
                log.error("Retention: ошибка при удалении сообщений старше {}", cutoff, e);
                return;
            }
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }

        if (total > 0) {
            log.info("Retention: удалено {} сообщений старше {}", total, cutoff);
        }
    }
}
