package org.example.helptreeservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminAnonymizeRequest {

    @NotBlank(message = "Причина анонимизации обязательна")
    @Size(max = 1000, message = "Причина не должна превышать 1000 символов")
    private String reason;

    /**
     * Optional safety check: when supplied, it must equal the account's current
     * email. It prevents anonymizing the wrong account from a stale tab.
     */
    private String confirmEmail;
}
