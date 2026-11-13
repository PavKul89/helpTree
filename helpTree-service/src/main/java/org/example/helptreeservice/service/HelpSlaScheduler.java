package org.example.helptreeservice.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.HelpProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SLA sweeps for ACCEPTED and COMPLETED helps.
 *
 * <ul>
 *   <li>{@link #sendSlaReminders()} notifies both participants when 50% and 80%
 *       of {@code acceptedTimeoutHours} have elapsed. Each threshold is recorded
 *       on the help row, so a restart or a second instance cannot duplicate it.</li>
 *   <li>{@link #cancelStaleAcceptedHelps()} keeps the existing automatic
 *       cancellation at 100% of the timeout.</li>
 *   <li>{@link #confirmStaleCompletedHelps()} automatically confirms completed
 *       helps after {@code completedTimeoutHours} have elapsed.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HelpSlaScheduler {

    private final HelpService helpService;
    private final HelpProperties helpProperties;

    @Scheduled(cron = "${app.help.reminder-cron:0 0 * * * *}")
    @SchedulerLock(name = "HelpSlaScheduler_sendSlaReminders",
            lockAtMostFor = "PT55M", lockAtLeastFor = "PT1M")
    public void sendSlaReminders() {
        if (!helpProperties.isSlaEnabled() || !helpProperties.isRemindersEnabled()) {
            return;
        }

        long timeoutMinutes = Math.max(1L, helpProperties.getAcceptedTimeoutHours() * 60L);
        int firstPercent = Math.min(99, Math.max(1, helpProperties.getReminderFirstPercent()));
        int secondPercent = Math.min(99, Math.max(firstPercent, helpProperties.getReminderSecondPercent()));

        LocalDateTime now = LocalDateTime.now();
        // A threshold is reached once that share of the timeout has elapsed:
        // 80% means the help was accepted 0.8 × timeout ago.
        LocalDateTime cutoffFirst = now.minusMinutes(
                Math.round(timeoutMinutes * firstPercent / 100.0));
        LocalDateTime cutoffSecond = now.minusMinutes(
                Math.round(timeoutMinutes * secondPercent / 100.0));

        List<Long> ids = helpService.findHelpIdsForSlaReminder(
                cutoffFirst, cutoffSecond, helpProperties.getReminderBatchSize());
        if (ids.isEmpty()) {
            return;
        }

        int sent = 0;
        for (Long helpId : ids) {
            try {
                if (helpService.sendSlaReminder(helpId, cutoffFirst, cutoffSecond)) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("Не удалось отправить SLA-напоминание helpId={}", helpId, e);
            }
        }

        log.info("SLA: отправлено {} из {} напоминаний", sent, ids.size());
    }

    @Scheduled(cron = "${app.help.sla-cron:0 0 * * * *}")
    @SchedulerLock(name = "HelpSlaScheduler_cancelStaleAcceptedHelps",
            lockAtMostFor = "PT55M", lockAtLeastFor = "PT1M")
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

    @Scheduled(cron = "${app.help.sla-cron:0 0 * * * *}")
    @SchedulerLock(name = "HelpSlaScheduler_confirmStaleCompletedHelps",
            lockAtMostFor = "PT55M", lockAtLeastFor = "PT1M")
    public void confirmStaleCompletedHelps() {
        if (!helpProperties.isSlaEnabled() || !helpProperties.isAutoConfirmEnabled()) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now()
                .minusHours(helpProperties.getCompletedTimeoutHours());
        List<Long> staleIds = helpService.findStaleCompletedHelpIds(
                cutoff, helpProperties.getSlaBatchSize());

        if (staleIds.isEmpty()) {
            return;
        }

        int confirmed = 0;
        for (Long helpId : staleIds) {
            try {
                if (helpService.autoConfirmStaleCompletedHelp(helpId, cutoff)) {
                    confirmed++;
                }
            } catch (Exception e) {
                log.error("Не удалось автоматически подтвердить помощь helpId={}", helpId, e);
            }
        }

        log.info("SLA: автоматически подтверждено {} из {} зависших COMPLETED",
                confirmed, staleIds.size());
    }
}
