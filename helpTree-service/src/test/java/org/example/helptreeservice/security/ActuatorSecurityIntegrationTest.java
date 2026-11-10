package org.example.helptreeservice.security;

import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.AuthSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "SWAGGER_ENABLED=false"
        })
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class ActuatorSecurityIntegrationTest {

    private static final String EMAIL_PREFIX = "actuator-security-";
    private static final String EMAIL_SUFFIX = "@test.local";
    private static final String SCRAPE_USERNAME = "scraper-test";
    private static final String SCRAPE_PASSWORD = "Local-Scraper-Only-42!";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Autowired private UserRepository userRepository;
    @Autowired private AuthSessionService authSessionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @LocalServerPort private int serverPort;
    @LocalManagementPort private int managementPort;

    private User user;
    private User admin;
    private AuthSessionService.IssuedTokens userTokens;
    private AuthSessionService.IssuedTokens adminTokens;

    @BeforeEach
    void setUp() {
        user = createUser(Role.USER);
        admin = createUser(Role.ADMIN);
        userTokens = authSessionService.createSession(user, "192.0.2.10", "actuator-security-test");
        adminTokens = authSessionService.createSession(admin, "192.0.2.11", "actuator-security-test");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                DELETE FROM auth_refresh_tokens
                WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)
                """, EMAIL_PREFIX + "%" + EMAIL_SUFFIX);
        jdbcTemplate.update("""
                DELETE FROM auth_sessions
                WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)
                """, EMAIL_PREFIX + "%" + EMAIL_SUFFIX);
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?",
                EMAIL_PREFIX + "%" + EMAIL_SUFFIX);
    }

    @Test
    void actuatorHealthIsPublicAndMetricsAndLoggersRequireAdmin() throws Exception {
        assertStatus(managementPort, "/actuator/health", null, 200);
        assertStatus(managementPort, "/actuator/metrics", null, 401);
        assertStatus(managementPort, "/actuator/loggers", null, 401);

        assertStatus(managementPort, "/actuator/metrics",
                bearer(userTokens.accessToken()), 403);
        assertStatus(managementPort, "/actuator/loggers",
                bearer(userTokens.accessToken()), 403);

        assertStatus(managementPort, "/actuator/metrics",
                bearer(adminTokens.accessToken()), 200);
    }

    @Test
    void prometheusRequiresDedicatedBasicCredentialsAndSwaggerIsDisabled() throws Exception {
        assertStatus(managementPort, "/actuator/prometheus", null, 401);
        assertStatus(managementPort, "/actuator/prometheus",
                bearer(adminTokens.accessToken()), 401);

        String basicCredentials = Base64.getEncoder().encodeToString(
                (SCRAPE_USERNAME + ":" + SCRAPE_PASSWORD).getBytes(StandardCharsets.UTF_8));
        assertStatus(managementPort, "/actuator/prometheus",
                "Basic " + basicCredentials, 200);
        assertStatus(serverPort, "/api/users/me",
                "Basic " + basicCredentials, 401);

        assertStatus(serverPort, "/actuator/prometheus", null, 401);
        assertStatus(serverPort, "/v3/api-docs", null, 404);
    }

    private void assertStatus(int port, String path, String authorization, int expectedStatus)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + path)).GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        HttpResponse<Void> response = HTTP_CLIENT.send(
                request.build(), HttpResponse.BodyHandlers.discarding());
        org.junit.jupiter.api.Assertions.assertEquals(expectedStatus, response.statusCode(),
                path + " on port " + port);
    }

    private User createUser(Role role) {
        User account = new User();
        account.setName("Actuator test " + role);
        account.setEmail(EMAIL_PREFIX + UUID.randomUUID() + EMAIL_SUFFIX);
        account.setPassword("$2a$10$test-only-not-a-real-hash");
        account.setRole(role);
        account.setEmailVerified(true);
        account.setHelpCoins(0L);
        account.setDebtCount(0);
        account.setHelpedCount(0);
        account.setDeleted(false);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        return userRepository.saveAndFlush(account);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
