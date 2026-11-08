package org.example.helptreeservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

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
        environment.setProperty("management.endpoints.web.exposure.include",
                "health,info,prometheus,metrics,loggers");
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
}
