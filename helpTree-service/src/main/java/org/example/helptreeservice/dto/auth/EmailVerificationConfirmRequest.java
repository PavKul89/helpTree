package org.example.helptreeservice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailVerificationConfirmRequest {

    @NotBlank(message = "Токен обязателен")
    private String token;
}
