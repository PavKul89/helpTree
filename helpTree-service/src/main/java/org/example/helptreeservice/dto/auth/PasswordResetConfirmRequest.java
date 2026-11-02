package org.example.helptreeservice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetConfirmRequest {

    @NotBlank(message = "Токен обязателен")
    private String token;

    @NotBlank(message = "Новый пароль обязателен")
    @Size(min = 8, max = 100, message = "Пароль должен быть от 8 до 100 символов")
    private String newPassword;
}
