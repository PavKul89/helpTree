package org.example.helptreeservice.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    /** Internal transport only: exposed to the browser as an HttpOnly cookie. */
    @JsonIgnore
    private String refreshToken;
    private Long userId;
    private String email;
    private String role;
    private Long expiresIn;
    private LocalDateTime previousLastLogin;
    private String sessionId;
    private Boolean emailVerified;

    public AuthResponse(String accessToken, String refreshToken, Long userId, String email, String role, Long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.expiresIn = expiresIn;
    }
}
