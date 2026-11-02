package org.example.helptreeservice.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailChangeRequest {

    @NotBlank(message = "Новый email обязателен")
    @Email(message = "Некорректный email")
    private String newEmail;

    @NotBlank(message = "Пароль обязателен")
    private String password;
}
