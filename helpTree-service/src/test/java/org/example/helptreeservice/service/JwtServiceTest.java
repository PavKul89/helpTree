package org.example.helptreeservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET = "mySecretKeyForJwtTokenSigning1234567890";
    private static final String SESSION_ID = UUID.randomUUID().toString();
    private static final long EXPIRATION_MS = 86400000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", EXPIRATION_MS);
    }

    @Test
    void generateToken_shouldReturnNonEmptyToken() {
        String token = jwtService.generateToken(1L, "test@email.com", "USER", SESSION_ID, 0L);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateToken_shouldContainCorrectClaims() {
        String token = jwtService.generateToken(42L, "pavel@test.com", "ADMIN", SESSION_ID, 3L);

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
        String token = jwtService.generateToken(1L, "user@example.com", "USER", SESSION_ID, 0L);

        String email = jwtService.extractEmail(token);

        assertEquals("user@example.com", email);
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {
        String token = jwtService.generateToken(1L, "test@email.com", "USER", SESSION_ID, 0L);

        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_shouldReturnFalseForTamperedToken() {
        String token = jwtService.generateToken(1L, "test@email.com", "USER", SESSION_ID, 0L);
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

    @Test
    void generateToken_shouldMarkTokenAsAccessToken() {
        String token = jwtService.generateToken(1L, "test@email.com", "USER", SESSION_ID, 0L);

        Claims claims = jwtService.parseClaims(token);

        assertEquals(JwtService.TOKEN_TYPE_ACCESS,
                claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class));
        assertNotNull(jwtService.parseAccessClaims(token));
    }

    @Test
    void generateWsTicket_shouldCreateShortLivedTicketThatIsNotAnAccessToken() {
        String ticket = jwtService.generateWsTicket(
                1L, "test@email.com", "USER", SESSION_ID, 0L, 60);

        Claims claims = jwtService.parseClaims(ticket);

        assertEquals(JwtService.TOKEN_TYPE_WS_TICKET,
                claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class));
        assertNotNull(jwtService.parseWsTicket(ticket));
        assertThrows(UnauthorizedException.class,
                () -> jwtService.parseAccessClaims(ticket));

        long ttlMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertTrue(ttlMs <= 60_000L);
        assertTrue(ttlMs > 0L);
    }

    @Test
    void parseWsTicket_shouldRejectAccessToken() {
        String token = jwtService.generateToken(1L, "test@email.com", "USER", SESSION_ID, 0L);

        assertThrows(UnauthorizedException.class,
                () -> jwtService.parseWsTicket(token));
    }

    @Test
    void parseAccessClaims_shouldAcceptLegacyTokenWithoutTypeClaim() {
        Date now = new Date();
        String legacyToken = Jwts.builder()
                .subject("legacy@test.com")
                .claim(JwtService.CLAIM_USER_ID, 5L)
                .claim(JwtService.CLAIM_ROLE, "USER")
                .claim(JwtService.CLAIM_SESSION_ID, SESSION_ID)
                .claim(JwtService.CLAIM_TOKEN_VERSION, 1L)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        Claims claims = jwtService.parseAccessClaims(legacyToken);

        assertEquals(5L, ((Number) claims.get(JwtService.CLAIM_USER_ID)).longValue());
        assertThrows(UnauthorizedException.class,
                () -> jwtService.parseWsTicket(legacyToken));
    }

    @Test
    void parseAccessClaims_shouldRejectUnknownTokenType() {
        Date now = new Date();
        String unknown = Jwts.builder()
                .subject("unknown@test.com")
                .claim(JwtService.CLAIM_USER_ID, 5L)
                .claim(JwtService.CLAIM_TOKEN_TYPE, "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(UnauthorizedException.class,
                () -> jwtService.parseAccessClaims(unknown));
    }
}

