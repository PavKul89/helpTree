package org.example.helptreeservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fail-fast validation of the production configuration.
 *
 * <p>Active only with the {@code prod} profile. Missing variables already break
 * startup through unresolved placeholders; this bean closes the remaining gap:
 * weak, short or well-known sample secrets, wildcard actuator exposure, an
 * insecure bootstrap administrator and a relay without credentials.</p>
 *
 * <p>Secrets are never printed: violations name the property, not its value.</p>
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionConfigValidator implements InitializingBean {

    static final int MIN_JWT_SECRET_LENGTH = 64;
    static final int MIN_DB_PASSWORD_LENGTH = 12;
    static final int MIN_STORAGE_SECRET_LENGTH = 16;
    static final int MIN_PROMETHEUS_SCRAPE_PASSWORD_LENGTH = 16;
    static final int MIN_RELAY_SECRET_LENGTH = 8;

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateSecrets(errors);
        validateBootstrapAdmin(errors);
        validateActuator(errors);
        validateOrigins(errors, warnings);
        validateOperations(warnings);
        validateLimits(errors);

        if (!warnings.isEmpty()) {
            warnings.forEach(warning -> log.warn("Production configuration warning: {}", warning));
        }
        if (!errors.isEmpty()) {
            String message = "Production configuration is invalid:\n - "
                    + String.join("\n - ", errors);
            log.error(message);
            throw new IllegalStateException(message);
        }
        log.info("Production configuration validation passed");
    }

    private void validateSecrets(List<String> errors) {
        if (SecretRules.isWeak(environment.getProperty("app.jwtSecret"), MIN_JWT_SECRET_LENGTH)) {
            errors.add("JWT_SECRET must be a random value of at least "
                    + MIN_JWT_SECRET_LENGTH + " characters and must not contain a sample/default marker");
        }
        if (SecretRules.isWeak(environment.getProperty("spring.datasource.password"),
                MIN_DB_PASSWORD_LENGTH)) {
            errors.add("DB_PASS must be a non-default value of at least "
                    + MIN_DB_PASSWORD_LENGTH + " characters");
        }
        if (SecretRules.isWeak(environment.getProperty("silo.secret-key"),
                MIN_STORAGE_SECRET_LENGTH)) {
            errors.add("SILO_SECRET_KEY must be a non-default value of at least "
                    + MIN_STORAGE_SECRET_LENGTH + " characters");
        }
        if (SecretRules.isWeak(environment.getProperty("silo.access-key"), 3)) {
            errors.add("SILO_ACCESS_KEY must be set to a non-default storage access key");
        }
        if (SecretRules.isWeak(environment.getProperty("app.metrics.prometheus.password"),
                MIN_PROMETHEUS_SCRAPE_PASSWORD_LENGTH)) {
            errors.add("PROMETHEUS_SCRAPE_PASSWORD must be a non-default value of at least "
                    + MIN_PROMETHEUS_SCRAPE_PASSWORD_LENGTH + " characters");
        }

        boolean relayEnabled = environment.getProperty("app.websocket.relay-enabled",
                Boolean.class, false);
        if (relayEnabled) {
            requireRelaySecret(errors, "WS_RELAY_CLIENT_PASSCODE",
                    environment.getProperty("app.websocket.client-passcode"));
            requireRelaySecret(errors, "WS_RELAY_SYSTEM_PASSCODE",
                    environment.getProperty("app.websocket.system-passcode"));
        }

        String telegramToken = environment.getProperty("app.notifications.telegram-bot-token");
        if (telegramToken != null && !telegramToken.isBlank()
                && SecretRules.isWeak(telegramToken, 20)) {
            errors.add("TELEGRAM_BOT_TOKEN is set but does not look like a real token");
        }
    }

    private void requireRelaySecret(List<String> errors, String name, String value) {
        if (SecretRules.isWeak(value, MIN_RELAY_SECRET_LENGTH)) {
            errors.add(name + " is required when WS_RELAY_ENABLED=true and must not be a default");
        }
    }

    private void validateBootstrapAdmin(List<String> errors) {
        boolean bootstrap = environment.getProperty("app.admin.bootstrap-enabled",
                Boolean.class, false);
        if (!bootstrap) {
            return;
        }
        String email = environment.getProperty("app.admin.email");
        if (!SecretRules.isEmail(email)) {
            errors.add("ADMIN_EMAIL must be a valid address when ADMIN_BOOTSTRAP_ENABLED=true");
        }
        if (!SecretRules.isStrongPassword(environment.getProperty("app.admin.password"))) {
            errors.add("ADMIN_PASSWORD must be a strong password (12+ characters, "
                    + "3 character classes, not a sample value) when "
                    + "ADMIN_BOOTSTRAP_ENABLED=true");
        }
    }

    private void validateActuator(List<String> errors) {
        String include = environment.getProperty("management.endpoints.web.exposure.include", "");
        String normalized = include.toLowerCase(Locale.ROOT);
        if (normalized.contains("*")) {
            errors.add("management.endpoints.web.exposure.include must not contain '*'");
        }
        for (String forbidden : List.of("env", "configprops", "heapdump", "threaddump", "shutdown")) {
            if (normalized.contains(forbidden)) {
                errors.add("Actuator endpoint '" + forbidden + "' must not be exposed");
            }
        }
    }

    private void validateOrigins(List<String> errors, List<String> warnings) {
        String cors = environment.getProperty("app.cors.allowed-origins", "");
        if (cors.contains("*")) {
            errors.add("CORS_ORIGINS must not contain '*'");
        } else if (containsLocalhost(cors)) {
            warnings.add("CORS_ORIGINS still contains localhost; replace it with the production frontend origin");
        }

        String websocket = environment.getProperty("app.websocket.allowed-origins", "");
        if (websocket.contains("*")) {
            errors.add("WS_ALLOWED_ORIGINS must not contain '*'");
        } else if (containsLocalhost(websocket)) {
            warnings.add("WS_ALLOWED_ORIGINS still contains localhost; replace it with the production frontend origin");
        }

        String frontend = environment.getProperty("app.auth.frontend-base-url", "");
        if (!frontend.toLowerCase(Locale.ROOT).startsWith("https://")) {
            warnings.add("FRONTEND_BASE_URL should use https in production");
        }
    }

    private boolean containsLocalhost(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("localhost") || normalized.contains("127.0.0.1");
    }

    private void validateOperations(List<String> warnings) {
        if (!environment.getProperty("app.rate-limit.enabled", Boolean.class, true)) {
            warnings.add("Rate limiting is disabled; only acceptable when an upstream gateway enforces it");
        }
        if (!environment.getProperty("app.login-protection.enabled", Boolean.class, true)) {
            warnings.add("Login protection is disabled; brute-force lockout is not active");
        }
        if (!environment.getProperty("app.economy.reconciliation-enabled", Boolean.class, true)) {
            warnings.add("Wallet reconciliation is disabled; balance drift will not be detected");
        }
        if (environment.getProperty("app.mail.enabled", Boolean.class, false)) {
            String mailHost = environment.getProperty("spring.mail.host", "");
            if (mailHost.isBlank() || mailHost.equalsIgnoreCase("localhost")) {
                warnings.add("MAIL_HOST is localhost while MAIL_ENABLED=true; email delivery will fail");
            }
        }
    }

    private void validateLimits(List<String> errors) {
        requirePositive(errors, "app.rate-limit.window-seconds", 60);
        requirePositive(errors, "app.rate-limit.default-limit", 600);
        requirePositive(errors, "app.rate-limit.login-limit", 10);
        requirePositive(errors, "app.jwtExpirationMs", 86_400_000L);
        requirePositive(errors, "upload.max-size", 10_485_760L);
        requirePositive(errors, "upload.max-pixels", 24_000_000L);
        requirePositive(errors, "upload.quota-bytes", 104_857_600L);
        requirePositive(errors, "app.economy.coins-per-help", 10L);
        requirePositive(errors, "app.help.accepted-timeout-hours", 168L);
        requirePositive(errors, "app.reports.sla-hours", 24L);
    }

    private void requirePositive(List<String> errors, String property, long defaultValue) {
        Long value = environment.getProperty(property, Long.class, defaultValue);
        if (value == null || value <= 0) {
            errors.add(property + " must be greater than zero");
        }
    }
}
