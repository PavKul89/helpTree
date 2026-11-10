package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.WalletProperties;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.NotificationType;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.notification.NotificationCommand;
import org.example.helptreeservice.service.notification.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Sends VIP expiry reminders at the configured day offsets (7/3/1 by default).
 *
 * <p>There is no auto-renewal: the only action is a notification. Repeating a
 * sweep on the same day is a no-op because the notification dedupe key includes
 * the user, the exact expiry timestamp and the day offset. Extending VIP creates
 * a new expiry timestamp, so the next period gets its own reminders.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaidFeatureExpiryScheduler {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final UserRepository userRepository;
    private final WalletProperties walletProperties;
    private final NotificationService notificationService;

    @Scheduled(cron = "${app.wallet.vip-reminder-cron:0 0 10 * * *}")
    public void sendVipExpiryReminders() {
        Set<Integer> reminderDays = parseDays(walletProperties.getVipReminderDays());
        if (reminderDays.isEmpty()) {
            return;
        }
        int maxDays = reminderDays.stream().mapToInt(Integer::intValue).max().orElse(0);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.plusDays(maxDays + 1L);

        List<User> users = userRepository.findVipExpiringBetween(
                now, cutoff, PageRequest.of(0, Math.max(1, walletProperties.getVipReminderBatchSize())));
        int sent = 0;
        for (User user : users) {
            try {
                if (user.getVipUntil() == null) {
                    continue;
                }
                long daysLeft = ChronoUnit.DAYS.between(now.toLocalDate(), user.getVipUntil().toLocalDate());
                if (daysLeft < 0 || !reminderDays.contains((int) daysLeft)) {
                    continue;
                }
                if (sendReminder(user, (int) daysLeft)) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("Не удалось отправить напоминание об окончании VIP userId={}", user.getId(), e);
            }
        }

        if (sent > 0) {
            log.info("VIP: отправлено {} напоминаний об окончании", sent);
        }
    }

    private boolean sendReminder(User user, int daysLeft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vipUntil", user.getVipUntil());
        payload.put("daysLeft", daysLeft);

        String body = "VIP-статус заканчивается " + DATE.format(user.getVipUntil())
                + " (через " + daysLeft + " дн.). Продлите его заранее, чтобы сохранить преимущества.";

        var created = notificationService.notify(NotificationCommand.of(
                user.getId(),
                NotificationType.PAID_FEATURE_EXPIRING,
                "VIP скоро закончится",
                body,
                payload,
                "vip-expiry:" + user.getId() + ":" + user.getVipUntil() + ":" + daysLeft));
        return created != null;
    }

    private Set<Integer> parseDays(String value) {
        Set<Integer> days = new TreeSet<>();
        if (value == null || value.isBlank()) {
            return days;
        }
        for (String part : value.split(",")) {
            try {
                int day = Integer.parseInt(part.trim());
                if (day > 0) {
                    days.add(day);
                }
            } catch (NumberFormatException ignored) {
                log.warn("Некорректное значение app.wallet.vip-reminder-days: {}", value);
            }
        }
        return days;
    }
}
