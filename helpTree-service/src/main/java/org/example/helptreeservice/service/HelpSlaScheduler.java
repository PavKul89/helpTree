package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.HelpProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cancels ACCEPTED helps that were never completed within the configured SLA.
 * There are no notification channels in the project, so the sweep only records
 * the system cancellation and reopens the post.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HelpSlaScheduler {

    private final HelpService helpService;
    private final HelpProperties helpProperties;

    @Scheduled(cron = "${app.help.sla-cron:0 0 * * * *}")
    public void cancelStaleAcceptedHelps() {
        if (!helpProperties.isSlaEnabled()) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now()
                .minusHours(helpProperties.getAcceptedTimeoutHours());
        List<Long> staleIds = helpService.findStaleAcceptedHelpIds(
                cutoff, helpProperties.getSlaBatchSize());

        if (staleIds.isEmpty()) {
            return;
        }

        int cancelled = 0;
        for (Long helpId : staleIds) {
            try {
                if (helpService.cancelStaleAcceptedHelp(helpId, cutoff)) {
                    cancelled++;
                }
            } catch (Exception e) {
                log.error("Не удалось автоматически отменить зависшую помощь helpId={}", helpId, e);
            }
        }

        log.info("SLA: автоматически отменено {} из {} зависших ACCEPTED", cancelled, staleIds.size());
    }
}
