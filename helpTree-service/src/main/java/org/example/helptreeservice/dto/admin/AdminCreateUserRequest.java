package org.example.helptreeservice.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.example.helptreeservice.enums.Role;

/**
 * Protected administrative creation: the administrator supplies the initial
 * password, so the account can be used immediately.
 */
@Data
public class AdminCreateUserRequest {

    @NotBlank(message = "Имя обязательно")
    @Size(min = 2, max = 100, message = "Имя должно быть от 2 до 100 символов")
    private String name;

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, max = 100, message = "Пароль должен быть от 8 до 100 символов")
    private String password;

    @NotBlank(message = "Телефон обязателен")
    @Size(min = 10, max = 20, message = "Телефон должен быть от 10 до 20 символов")
    private String phone;

    @NotBlank(message = "Город обязателен")
    private String city;

    /** Defaults to USER when omitted. */
    private Role role;

    @NotBlank(message = "Причина создания обязательна")
    @Size(max = 1000, message = "Причина не должна превышать 1000 символов")
    private String reason;
}
