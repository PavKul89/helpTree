package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.AuthConfig;
import org.example.helptreeservice.entity.AccountToken;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.AccountTokenType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.AccountTokenRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.email.EmailService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Password change/reset, email verification and email change flows.
 * All raw tokens are generated with high entropy, stored as SHA-256 hashes and
 * consumed under a pessimistic lock so they can be used only once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSecurityService {

    private final UserRepository userRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final PasswordService passwordService;
    private final AuthSessionService authSessionService;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final AuthConfig authConfig;

    @Transactional
    public void sendRegistrationVerification(User user) {
        if (user == null || Boolean.TRUE.equals(user.getEmailVerified()) || !emailService.isAvailable()) {
            return;
        }
        try {
            String rawToken = createAccountToken(user, AccountTokenType.EMAIL_VERIFICATION, null,
                    Duration.ofHours(emailVerificationHours()));
            if (!emailService.sendEmailVerification(user.getEmail(), rawToken)) {
                log.warn("Не удалось отправить письмо подтверждения email при регистрации: userId={}",
                        user.getId());
            }
        } catch (Exception e) {
            // Registration must not fail because the mail server is unavailable.
            log.error("Ошибка подготовки письма подтверждения email при регистрации: userId={}",
                    user.getId(), e);
        }
    }

    /**
     * Creates a single-use password-setup token for an invited account. The
     * caller has already persisted the user inside the same transaction, so a
     * delivery failure rolls the invitation back instead of leaving a locked
     * account nobody can access.
     */
    @Transactional
    public void sendAccountInvitation(User user) {
        if (user == null || user.getId() == null) {
            throw new BadRequestException("Пользователь для приглашения не найден");
        }
        if (!emailService.isAvailable()) {
            throw new BadRequestException("Отправка email не настроена: приглашение создать нельзя");
        }
        String rawToken = createAccountToken(user, AccountTokenType.PASSWORD_RESET, null,
                Duration.ofHours(invitationHours()));
        if (!emailService.sendAccountInvitation(user.getEmail(), rawToken)) {
            throw new BadRequestException("Не удалось отправить приглашение");
        }
        log.info("Приглашение отправлено пользователю {}", user.getId());
    }

    @Transactional
    public void requestEmailVerification(Long userId) {
        User user = requireActiveUser(userId);
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        if (!emailService.isAvailable()) {
            throw new BadRequestException("Отправка email не настроена");
        }

        String rawToken = createAccountToken(user, AccountTokenType.EMAIL_VERIFICATION, null,
                Duration.ofHours(emailVerificationHours()));
        if (!emailService.sendEmailVerification(user.getEmail(), rawToken)) {
            throw new BadRequestException("Не удалось отправить письмо подтверждения email");
        }
    }

    @Transactional
    public void confirmEmailVerification(String rawToken) {
        ConsumedToken consumed = requireUsableToken(rawToken, AccountTokenType.EMAIL_VERIFICATION);
        User user = consumed.user();

        LocalDateTime now = LocalDateTime.now();
        user.setEmailVerified(true);
        user.setUpdatedAt(now);
        userRepository.save(user);

        log.info("Email подтверждён для пользователя {}", user.getId());
    }

    /**
     * Always behaves the same for known and unknown addresses to avoid account
     * enumeration. The raw token is only delivered by email.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        if (!emailService.isAvailable()) {
            log.info("Запрос сброса пароля принят, но SMTP не настроен");
            return;
        }

        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .ifPresent(user -> {
                    String rawToken = createAccountToken(user, AccountTokenType.PASSWORD_RESET, null,
                            Duration.ofMinutes(passwordResetMinutes()));
                    if (!emailService.sendPasswordReset(user.getEmail(), rawToken)) {
                        log.warn("Не удалось отправить письмо сброса пароля: userId={}", user.getId());
                    }
                });
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        ConsumedToken consumed = requireUsableToken(rawToken, AccountTokenType.PASSWORD_RESET);
        User user = consumed.user();

        passwordService.validate(newPassword);
        LocalDateTime now = LocalDateTime.now();
        user.setPassword(passwordService.encode(newPassword));
        // Receiving this token proves control of the mailbox.
        user.setEmailVerified(true);
        user.setUpdatedAt(now);
        userRepository.save(user);

        authSessionService.revokeAllSessionsForUser(user.getId(), "PASSWORD_RESET");
        log.info("Пароль сброшен для пользователя {}", user.getId());
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }
        if (!passwordService.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Текущий пароль указан неверно");
        }
        if (passwordService.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("Новый пароль должен отличаться от текущего");
        }

        passwordService.validate(newPassword);
        user.setPassword(passwordService.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        authSessionService.revokeAllSessionsForUser(userId, "PASSWORD_CHANGED");
        log.info("Пароль изменён для пользователя {}", userId);
    }

    @Transactional
    public void requestEmailChange(Long userId, String newEmail, String password) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }
        if (!passwordService.matches(password, user.getPassword())) {
            throw new BadRequestException("Пароль указан неверно");
        }

        String normalizedEmail = newEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("Новый email совпадает с текущим");
        }
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Email уже используется");
        }
        if (!emailService.isAvailable()) {
            throw new BadRequestException("Отправка email не настроена");
        }

        String rawToken = createAccountToken(user, AccountTokenType.EMAIL_CHANGE, normalizedEmail,
                Duration.ofHours(emailChangeHours()));
        if (!emailService.sendEmailChangeConfirmation(normalizedEmail, normalizedEmail, rawToken)) {
            throw new BadRequestException("Не удалось отправить письмо для подтверждения нового адреса");
        }
        log.info("Запрошена смена email для пользователя {} (новый адрес замаскирован)", userId);
    }

    @Transactional
    public void confirmEmailChange(String rawToken) {
        ConsumedToken consumed = requireUsableToken(rawToken, AccountTokenType.EMAIL_CHANGE);
        AccountToken token = consumed.token();
        String newEmail = token.getPayload();
        if (newEmail == null || newEmail.isBlank()) {
            throw new BadRequestException("Ссылка недействительна или истекла");
        }

        User user = consumed.user();
        Long userId = user.getId();

        String oldEmail = user.getEmail();
        if (!newEmail.equalsIgnoreCase(oldEmail) && userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new ConflictException("Email уже используется");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setEmail(newEmail);
        user.setEmailVerified(true);
        user.setUpdatedAt(now);
        userRepository.save(user);

        // An email change alters the login identity, so every old session must die.
        authSessionService.revokeAllSessionsForUser(userId, "EMAIL_CHANGED");
        if (!newEmail.equalsIgnoreCase(oldEmail)) {
            emailService.sendEmailChangedNotice(oldEmail, newEmail);
        }
        log.info("Email изменён для пользователя {}", userId);
    }

    @Scheduled(cron = "0 30 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = accountTokenRepository.deleteExpiredBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Удалено {} истёкших account-токенов", deleted);
        }
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }
        return user;
    }

    private record ConsumedToken(AccountToken token, User user) {
    }

    /**
     * Locks the user row first and only then atomically consumes the token. Keeping
     * that lock order consistent with request* methods prevents deadlocks between
     * concurrent confirmation and re-issue requests.
     */
    private ConsumedToken requireUsableToken(String rawToken, AccountTokenType expectedType) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Токен обязателен");
        }

        String tokenHash = tokenService.hash(rawToken);
        Long userId = accountTokenRepository.findUserIdByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Ссылка недействительна или истекла"));

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BadRequestException("Ссылка недействительна или истекла"));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new BadRequestException("Ссылка недействительна или истекла");
        }

        LocalDateTime now = LocalDateTime.now();
        int consumed = accountTokenRepository.consume(tokenHash, expectedType, now);
        if (consumed != 1) {
            throw new BadRequestException("Ссылка недействительна или истекла");
        }

        AccountToken token = accountTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Ссылка недействительна или истекла"));
        return new ConsumedToken(token, user);
    }

    private String createAccountToken(User user, AccountTokenType type, String payload, Duration lifetime) {
        LocalDateTime now = LocalDateTime.now();
        accountTokenRepository.revokeActiveByUserAndType(user.getId(), type, now);

        String rawToken = tokenService.generate();
        AccountToken token = AccountToken.builder()
                .user(user)
                .type(type)
                .tokenHash(tokenService.hash(rawToken))
                .payload(payload != null ? payload.toLowerCase(Locale.ROOT) : null)
                .createdAt(now)
                .expiresAt(now.plus(lifetime))
                .build();
        accountTokenRepository.save(token);
        return rawToken;
    }

    private int passwordResetMinutes() {
        return Math.max(1, authConfig.getPasswordResetTokenMinutes());
    }

    private int invitationHours() {
        return Math.max(1, authConfig.getAccountInvitationHours());
    }

    private int emailVerificationHours() {
        return Math.max(1, authConfig.getEmailVerificationTokenHours());
    }

    private int emailChangeHours() {
        return Math.max(1, authConfig.getEmailChangeTokenHours());
    }
}
