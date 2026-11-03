package org.example.helptreeservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_SESSION_ID = "sessionId";
    public static final String CLAIM_TOKEN_VERSION = "tokenVersion";
    public static final String CLAIM_TOKEN_TYPE = "typ";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_WS_TICKET = "ws-ticket";

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs}")
    private long jwtExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String email, String role, String sessionId, long tokenVersion) {
        return generateToken(userId, email, role, sessionId, tokenVersion,
                TOKEN_TYPE_ACCESS, jwtExpirationMs);
    }

    /**
     * One-purpose ticket used by the WebSocket handshake. Browsers cannot set an
     * Authorization header on a WebSocket upgrade, so the client asks the REST API
     * for a short-lived ticket and puts it in the connection URL. The ticket is not
     * an access token and is rejected by all HTTP authentication code paths.
     */
    public String generateWsTicket(Long userId,
                                   String email,
                                   String role,
                                   String sessionId,
                                   long tokenVersion,
                                   int ttlSeconds) {
        long ttlMs = Math.max(1, ttlSeconds) * 1000L;
        return generateToken(userId, email, role, sessionId, tokenVersion,
                TOKEN_TYPE_WS_TICKET, ttlMs);
    }

    private String generateToken(Long userId,
                                 String email,
                                 String role,
                                 String sessionId,
                                 long tokenVersion,
                                 String tokenType,
                                 long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_SESSION_ID, sessionId)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Parses an access token and rejects any other token type. Legacy access
     * tokens issued before the token type claim existed are still accepted.
     */
    public Claims parseAccessClaims(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (tokenType != null && !TOKEN_TYPE_ACCESS.equals(tokenType)) {
            throw new UnauthorizedException("Невалидный токен");
        }
        return claims;
    }

    /**
     * Parses a WebSocket handshake ticket. Access tokens are deliberately rejected
     * here so a stolen long-lived token cannot be replayed through the URL.
     */
    public Claims parseWsTicket(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_WS_TICKET.equals(tokenType)) {
            throw new UnauthorizedException("Невалидный WebSocket-тикет");
        }
        return claims;
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpirationTime() {
        return jwtExpirationMs;
    }
}
