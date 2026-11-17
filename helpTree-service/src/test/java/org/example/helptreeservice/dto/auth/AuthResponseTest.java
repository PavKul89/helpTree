package org.example.helptreeservice.dto.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void refreshTokenIsNeverSerialisedIntoTheJsonBody() throws Exception {
        AuthResponse response = new AuthResponse("access", "refresh", 1L,
                "user@test.com", "USER", 1_800_000L);

        String json = mapper.writeValueAsString(response);

        assertTrue(json.contains("\"accessToken\":\"access\""));
        assertFalse(json.contains("refresh"));
        assertFalse(json.contains("\"refreshToken\""));
    }
}
