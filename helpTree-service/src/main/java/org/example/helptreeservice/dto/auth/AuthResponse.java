package org.example.helptreeservice.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String email;
    private String role;
    private Long expiresIn;
    private LocalDateTime previousLastLogin;

    public AuthResponse(String accessToken, String refreshToken, Long userId, String email, String role, Long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.expiresIn = expiresIn;
    }
}
