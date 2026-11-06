package org.example.helptreeservice.dto.account;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Self-service anonymization requires the current password, so a stolen access
 * token alone cannot erase an account.
 */
@Data
public class AccountAnonymizeRequest {

    @NotBlank(message = "Пароль обязателен")
    private String password;

    /** Optional reason retained in the audit log for user support. */
    private String reason;
}
