package org.example.helptreeservice.service.email;

import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.AuthConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final AuthConfig authConfig;
    private final String fromAddress;

    public SmtpEmailService(JavaMailSender mailSender,
                            AuthConfig authConfig,
                            @Value("${app.mail.from:no-reply@helptree.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.authConfig = authConfig;
        this.fromAddress = fromAddress;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean sendEmailVerification(String to, String token) {
        return send(to,
                "Подтвердите email в HelpTree",
                "Здравствуйте!\n\n"
                        + "Подтвердите адрес электронной почты, перейдя по ссылке:\n"
                        + link("/verify-email", token)
                        + "\n\nЕсли вы не регистрировались в HelpTree, проигнорируйте это письмо.");
    }

    @Override
    public boolean sendPasswordReset(String to, String token) {
        return send(to,
                "Восстановление пароля HelpTree",
                "Здравствуйте!\n\n"
                        + "Для смены пароля перейдите по ссылке:\n"
                        + link("/reset-password", token)
                        + "\n\nСсылка действует ограниченное время. Если вы не запрашивали смену пароля, "
                        + "проигнорируйте письмо.");
    }

    @Override
    public boolean sendEmailChangeConfirmation(String to, String newEmail, String token) {
        return send(to,
                "Подтвердите новый email в HelpTree",
                "Здравствуйте!\n\n"
                        + "Подтвердите смену адреса на " + newEmail + ":\n"
                        + link("/confirm-email-change", token)
                        + "\n\nЕсли вы не запрашивали смену адреса, проигнорируйте письмо.");
    }

    @Override
    public boolean sendAccountInvitation(String to, String token) {
        return send(to,
                "Приглашение в HelpTree",
                "Здравствуйте!\n\n"
                        + "Администратор HelpTree создал для вас аккаунт.\n"
                        + "Чтобы задать пароль и войти, перейдите по ссылке:\n"
                        + link("/reset-password", token)
                        + "\n\nСсылка действует ограниченное время. Если вы не ожидали приглашение, "
                        + "проигнорируйте это письмо.");
    }

    @Override
    public void sendEmailChangedNotice(String oldEmail, String newEmail) {
        send(oldEmail,
                "Email в HelpTree изменён",
                "Адрес электронной почты вашего аккаунта был изменён на " + newEmail + ".\n"
                        + "Если это были не вы, немедленно свяжитесь с поддержкой.");
    }

    private boolean send(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            return true;
        } catch (MessagingException | MailException e) {
            log.error("Не удалось отправить письмо '{}' на {}", subject, to, e);
            return false;
        }
    }

    private String link(String path, String token) {
        return authConfig.getFrontendBaseUrl() + path + "?token=" + token;
    }
}
