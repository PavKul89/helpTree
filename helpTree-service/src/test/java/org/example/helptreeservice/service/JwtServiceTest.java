package org.example.helptreeservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET = "mySecretKeyForJwtTokenSigning1234567890";
    private static final long EXPIRATION_MS = 86400000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", EXPIRATION_MS);
    }

    @Test
    void generateToken_shouldReturnNonEmptyToken() {
        String token = jwtService.generateToken(1L, "test@email.com", "USER");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateToken_shouldContainCorrectClaims() {
        String token = jwtService.generateToken(42L, "pavel@test.com", "ADMIN");

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("pavel@test.com", claims.getSubject());
        assertEquals(42, ((Number) claims.get("userId")).longValue());
        assertEquals("ADMIN", claims.get("role"));
    }

    @Test
    void extractEmail_shouldReturnCorrectEmail() {
        String token = jwtService.generateToken(1L, "user@example.com", "USER");

        String email = jwtService.extractEmail(token);

        assertEquals("user@example.com", email);
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {
        String token = jwtService.generateToken(1L, "test@email.com", "USER");

        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_shouldReturnFalseForTamperedToken() {
        String token = jwtService.generateToken(1L, "test@email.com", "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertFalse(jwtService.isTokenValid(tampered));
    }

    @Test
    void isTokenValid_shouldReturnFalseForRandomString() {
        assertFalse(jwtService.isTokenValid("not.a.valid.token"));
    }

    @Test
    void getExpirationTime_shouldReturnConfiguredValue() {
        assertEquals(EXPIRATION_MS, jwtService.getExpirationTime());
    }
}
