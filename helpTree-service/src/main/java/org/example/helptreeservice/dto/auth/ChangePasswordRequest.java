package org.example.helptreeservice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Текущий пароль обязателен")
    private String currentPassword;

    @NotBlank(message = "Новый пароль обязателен")
    @Size(min = 8, max = 100, message = "Пароль должен быть от 8 до 100 символов")
    private String newPassword;
}
