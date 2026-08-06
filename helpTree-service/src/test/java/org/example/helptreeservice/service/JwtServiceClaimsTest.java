package org.example.helptreeservice.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceClaimsTest {

    private JwtService jwtService;
    private static final String SECRET = "testSecretKeyForJwtClaimsTesting123456";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 86400000L);
    }

    @Test
    void generatedToken_shouldBeParseableAndContainUserId() {
        String token = jwtService.generateToken(7L, "pavel@test.com", "USER");

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Object userIdClaim = claims.get("userId");
        assertNotNull(userIdClaim);
        assertTrue(userIdClaim instanceof Number);
        assertEquals(7, ((Number) userIdClaim).longValue());
    }

    @Test
    void generateToken_withZeroUserId() {
        String token = jwtService.generateToken(0L, "zero@test.com", "USER");

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals(0, ((Number) claims.get("userId")).longValue());
    }

    @Test
    void generateToken_withLargeUserId() {
        String token = jwtService.generateToken(999999L, "big@test.com", "ADMIN");

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals(999999, ((Number) claims.get("userId")).longValue());
        assertEquals("ADMIN", claims.get("role"));
    }
}
