package org.example.helptreeservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.example.helptreeservice.enums.Role;

@Data
public class AdminRoleChangeRequest {

    @NotNull(message = "Роль обязательна")
    private Role role;

    @NotBlank(message = "Причина смены роли обязательна")
    @Size(max = 1000, message = "Причина не должна превышать 1000 символов")
    private String reason;
}
