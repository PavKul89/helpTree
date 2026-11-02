package org.example.helptreeservice.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

public final class TestJwtUtils {

    private TestJwtUtils() {}

    public static String generateToken(Long userId, String email, String role, String secret) {
        return generateToken(userId, email, role, UUID.randomUUID().toString(), 0L, secret);
    }

    public static String generateToken(Long userId,
                                       String email,
                                       String role,
                                       String sessionId,
                                       long tokenVersion,
                                       String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("sessionId", sessionId)
                .claim("tokenVersion", tokenVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(key)
                .compact();
    }
}
