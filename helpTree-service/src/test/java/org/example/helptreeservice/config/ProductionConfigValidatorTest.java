package org.example.helptreeservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigValidatorTest {

    private MockEnvironment validEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.jwtSecret", "Q7vN3xR8mK2pT9sL4wY6zB1cD5fG0hJ".repeat(3));
        environment.setProperty("spring.datasource.password", "S3cure-Db-Passphrase");
        environment.setProperty("silo.secret-key", "Zx9vQ2mLp8Rt4Yw6");
        environment.setProperty("silo.access-key", "helptree-storage");
        environment.setProperty("app.metrics.prometheus.password", "Pr0mScrape-Only-Entropy-42!");
        environment.setProperty("app.admin.email", "admin@example.org");
        environment.setProperty("app.admin.password", "A-Strong-Admin-Pass-42!");
        environment.setProperty("app.websocket.client-passcode", "client-relay-pass-42");
        environment.setProperty("app.websocket.system-passcode", "system-relay-pass-42");
        environment.setProperty("management.endpoints.web.exposure.include",
                "health,metrics,info,prometheus");
        environment.setProperty("app.cors.allowed-origins", "https://helptree.example.org");
        environment.setProperty("app.websocket.allowed-origins", "https://helptree.example.org");
        environment.setProperty("app.auth.frontend-base-url", "https://helptree.example.org");
        return environment;
    }

    @Test
    void validProductionConfigurationPasses() {
        assertDoesNotThrow(() -> new ProductionConfigValidator(validEnvironment()).afterPropertiesSet());
    }

    @Test
    void weakJwtSecretAbortsStartupWithoutPrintingTheValue() {
        MockEnvironment environment = validEnvironment();
        environment.setProperty("app.jwtSecret", "dev-only-secret-not-for-production");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigValidator(environment).afterPropertiesSet());

        assertTrue(error.getMessage().contains("JWT_SECRET"));
        assertFalse(error.getMessage().contains("dev-only-secret-not-for-production"));
    }

    @Test
    void weakPrometheusScrapePasswordAbortsStartupWithoutPrintingTheValue() {
        MockEnvironment environment = validEnvironment();
        environment.setProperty("app.metrics.prometheus.password", "weak-scrape-password");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigValidator(environment).afterPropertiesSet());

        assertTrue(error.getMessage().contains("PROMETHEUS_SCRAPE_PASSWORD"));
        assertFalse(error.getMessage().contains("weak-scrape-password"));
    }

    @Test
    void wildcardActuatorExposureIsRejected() {
        MockEnvironment environment = validEnvironment();
        environment.setProperty("management.endpoints.web.exposure.include", "*");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigValidator(environment).afterPropertiesSet());

        assertTrue(error.getMessage().contains("management.endpoints.web.exposure.include"));
    }

    @Test
    void forbiddenActuatorEndpointIsRejected() {
        MockEnvironment environment = validEnvironment();
        environment.setProperty("management.endpoints.web.exposure.include",
                "health,env,heapdump");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigValidator(environment).afterPropertiesSet());

        assertTrue(error.getMessage().contains("env"));
        assertTrue(error.getMessage().contains("heapdump"));
    }

    @Test
    void insecureBootstrapAdministratorIsRejected() {
        MockEnvironment environment = validEnvironment();
        environment.setProperty("app.admin.bootstrap-enabled", "true");
        environment.setProperty("app.admin.email", "not-an-email");
        environment.setProperty("app.admin.password", "dev-admin-password");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigValidator(environment).afterPropertiesSet());

        assertTrue(error.getMessage().contains("ADMIN_EMAIL"));
        assertTrue(error.getMessage().contains("ADMIN_PASSWORD"));
    }

    @Test
    void relayWithoutCredentialsIsRejected() {
        MockEnvironment environment = validEnvironment();
        environment.setProperty("app.websocket.relay-enabled", "true");
        environment.setProperty("app.websocket.client-passcode", "");
        environment.setProperty("app.websocket.system-passcode", "");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigValidator(environment).afterPropertiesSet());

        assertTrue(error.getMessage().contains("WS_RELAY_CLIENT_PASSCODE"));
        assertTrue(error.getMessage().contains("WS_RELAY_SYSTEM_PASSCODE"));
    }

    @Test
    void wildcardCorsOriginIsRejected() {
        MockEnvironment environment = validEnvironment();
        environment.setProperty("app.cors.allowed-origins", "*");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigValidator(environment).afterPropertiesSet());

        assertTrue(error.getMessage().contains("CORS_ORIGINS"));
    }

    @Test
    void repositoryDefaultSecretsAndLocalOriginsAreRejectedIndividually() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("app.jwtSecret", "testSecretKeyForJwtTesting12345678901234567890");
        defaults.put("app.admin.password", "dev-admin-password-change-in-prod");
        defaults.put("app.admin.email", "admin@helptree.dev");
        defaults.put("silo.access-key", "helptree");
        defaults.put("silo.secret-key", "helptree-dev-password-change-me");
        defaults.put("spring.datasource.password", "helpTree");
        defaults.put("app.websocket.client-passcode", "guest");
        defaults.put("app.websocket.system-passcode", "guest");
        defaults.put("app.cors.allowed-origins", "http://localhost:3000");
        defaults.put("app.websocket.allowed-origins", "http://localhost:3000");
        defaults.put("app.auth.frontend-base-url", "http://localhost:3000");

        defaults.forEach((property, value) -> {
            MockEnvironment environment = validEnvironment();
            environment.setProperty(property, value);
            if (property.endsWith("passcode")) {
                environment.setProperty("app.websocket.relay-enabled", "true");
            }

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> new ProductionConfigValidator(environment).afterPropertiesSet(),
                    property);

            assertTrue(error.getMessage().contains(defaultPropertyName(property)), property);
            assertFalse(error.getMessage().contains(value), property);
        });
    }

    @Test
    void shortJwtSecretAbortsStartupWithoutPrintingTheValue() {
        MockEnvironment environment = validEnvironment();
        String secret = "short-secret";
        environment.setProperty("app.jwtSecret", secret);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ProductionConfigValidator(environment).afterPropertiesSet());

        assertTrue(error.getMessage().contains("JWT_SECRET"));
        assertFalse(error.getMessage().contains(secret));
    }

    @Test
    void guestRelayPasscodesAreAllowedWhenRelayIsDisabled() {
        MockEnvironment environment = validEnvironment();
        environment.setProperty("app.websocket.relay-enabled", "false");
        environment.setProperty("app.websocket.client-passcode", "guest");
        environment.setProperty("app.websocket.system-passcode", "guest");

        assertDoesNotThrow(() -> new ProductionConfigValidator(environment).afterPropertiesSet());
    }

    @Test
    void validatorIsOnlyRegisteredForProductionProfile() {
        Profile profile = ProductionConfigValidator.class.getAnnotation(Profile.class);

        assertTrue(profile != null);
        assertTrue(java.util.Arrays.asList(profile.value()).contains("prod"));
    }

    private String defaultPropertyName(String property) {
        return switch (property) {
            case "app.jwtSecret" -> "JWT_SECRET";
            case "app.admin.password" -> "ADMIN_PASSWORD";
            case "app.admin.email" -> "ADMIN_EMAIL";
            case "silo.access-key" -> "SILO_ACCESS_KEY";
            case "silo.secret-key" -> "SILO_SECRET_KEY";
            case "spring.datasource.password" -> "DB_PASS";
            case "app.websocket.client-passcode" -> "WS_RELAY_CLIENT_PASSCODE";
            case "app.websocket.system-passcode" -> "WS_RELAY_SYSTEM_PASSCODE";
            case "app.cors.allowed-origins" -> "CORS_ORIGINS";
            case "app.websocket.allowed-origins" -> "WS_ALLOWED_ORIGINS";
            case "app.auth.frontend-base-url" -> "FRONTEND_BASE_URL";
            default -> throw new IllegalArgumentException("Unexpected property: " + property);
        };
    }
}
