package org.example.helptreeservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Mandatory human-readable justification for a critical administrative action.
 */
@Data
public class AdminReasonRequest {

    @NotBlank(message = "Причина обязательна")
    @Size(max = 1000, message = "Причина не должна превышать 1000 символов")
    private String reason;
}
