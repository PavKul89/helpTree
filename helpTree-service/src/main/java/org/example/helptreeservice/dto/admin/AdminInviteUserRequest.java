package org.example.helptreeservice.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.example.helptreeservice.enums.Role;

/**
 * Invitation flow: no password crosses the wire. The service creates a locked
 * account and emails a single-use password-setup link.
 */
@Data
public class AdminInviteUserRequest {

    @NotBlank(message = "Имя обязательно")
    @Size(min = 2, max = 100, message = "Имя должно быть от 2 до 100 символов")
    private String name;

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    private String email;

    @NotBlank(message = "Телефон обязателен")
    @Size(min = 10, max = 20, message = "Телефон должен быть от 10 до 20 символов")
    private String phone;

    @NotBlank(message = "Город обязателен")
    private String city;

    /** Defaults to USER when omitted. */
    private Role role;

    @NotBlank(message = "Причина приглашения обязательна")
    @Size(max = 1000, message = "Причина не должна превышать 1000 символов")
    private String reason;
}
