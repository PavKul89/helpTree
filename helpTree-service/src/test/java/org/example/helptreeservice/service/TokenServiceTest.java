package org.example.helptreeservice.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private final TokenService tokenService = new TokenService();

    @Test
    void generate_shouldProduceUniqueUrlSafeHighEntropyTokens() {
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            String token = tokenService.generate();

            assertTrue(token.matches("[A-Za-z0-9_-]+"), "Token must be URL-safe");
            assertTrue(token.length() >= 43, "32 random bytes must encode to at least 43 chars");
            assertTrue(seen.add(token), "Generated tokens must never repeat");
        }
    }

    @Test
    void hash_shouldBeDeterministicSha256Hex() {
        String first = tokenService.hash("refresh-token");
        String second = tokenService.hash("refresh-token");

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertNotEquals(first, tokenService.hash("another-refresh-token"));
    }

    @Test
    void hash_shouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> tokenService.hash(null));
    }
}
