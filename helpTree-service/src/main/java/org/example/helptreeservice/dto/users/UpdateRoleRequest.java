package org.example.helptreeservice.dto.users;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.helptreeservice.enums.Role;

@Data
public class UpdateRoleRequest {

    @NotNull(message = "Роль обязательна")
    private Role role;
}
