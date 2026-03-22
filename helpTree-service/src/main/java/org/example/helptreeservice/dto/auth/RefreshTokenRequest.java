package org.example.helptreeservice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    
    @NotBlank(message = "Refresh токен обязателен")
    private String refreshToken;
    
    @NotBlank(message = "ID пользователя обязателен")
    private String userId;
}
