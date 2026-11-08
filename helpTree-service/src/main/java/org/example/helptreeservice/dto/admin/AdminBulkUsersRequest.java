package org.example.helptreeservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** Bulk action over user ids: DELETE, RESTORE or CHANGE_ROLE. */
@Data
public class AdminBulkUsersRequest {

    @NotBlank(message = "Операция обязательна")
    private String action;

    @NotEmpty(message = "Список пользователей не может быть пустым")
    private List<Long> ids;

    private String role;

    @NotBlank(message = "Причина обязательна")
    private String reason;
}
