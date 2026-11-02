package org.example.helptreeservice.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordResetRequest {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    private String email;
}
