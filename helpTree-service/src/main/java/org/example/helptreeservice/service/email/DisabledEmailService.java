package org.example.helptreeservice.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Active when SMTP is not configured. Security flows stay enabled, but no message
 * is delivered and tokens are never written to logs by default.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledEmailService implements EmailService {

    public DisabledEmailService() {
        log.warn("SMTP не настроен (app.mail.enabled=false). Письма для подтверждения email, "
                + "сброса пароля и смены адреса отправляться не будут.");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean sendEmailVerification(String to, String token) {
        return logDisabled("email verification", to);
    }

    @Override
    public boolean sendPasswordReset(String to, String token) {
        return logDisabled("password reset", to);
    }

    @Override
    public boolean sendEmailChangeConfirmation(String to, String newEmail, String token) {
        return logDisabled("email change", to);
    }

    @Override
    public boolean sendAccountInvitation(String to, String token) {
        return logDisabled("account invitation", to);
    }

    @Override
    public void sendEmailChangedNotice(String oldEmail, String newEmail) {
        logDisabled("email changed notice", oldEmail);
    }

    @Override
    public boolean sendNotification(String to, String subject, String body) {
        return logDisabled("notification: " + subject, to);
    }

    private boolean logDisabled(String purpose, String to) {
        log.info("Письмо ({}) не отправлено: SMTP отключён. Получатель: {}", purpose, mask(to));
        return false;
    }

    private String mask(String email) {
        if (email == null || email.isBlank()) {
            return "<empty>";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
