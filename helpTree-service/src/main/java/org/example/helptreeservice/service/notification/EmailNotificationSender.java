package org.example.helptreeservice.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.enums.NotificationChannel;
import org.example.helptreeservice.service.email.EmailService;
import org.springframework.stereotype.Component;

/**
 * Email transport backed by the existing security mail sender. When SMTP is not
 * configured {@link EmailService#isAvailable()} is false and no outbox row is
 * created for the email channel in the first place.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final EmailService emailService;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean isAvailable() {
        return emailService.isAvailable();
    }

    @Override
    public boolean send(NotificationDelivery delivery) {
        if (delivery.recipient() == null || delivery.recipient().isBlank()) {
            throw new PermanentDeliveryException("У пользователя нет email");
        }
        return emailService.sendNotification(
                delivery.recipient(), delivery.title(), delivery.body());
    }
}
