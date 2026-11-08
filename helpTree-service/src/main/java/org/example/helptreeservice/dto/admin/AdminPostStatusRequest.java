package org.example.helptreeservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Administrative status change for a request. The reason is mandatory because
 * the transition is written to the audit trail.
 */
@Data
public class AdminPostStatusRequest {

    @NotBlank(message = "Статус обязателен")
    private String status;

    @NotBlank(message = "Причина обязательна")
    private String reason;
}
