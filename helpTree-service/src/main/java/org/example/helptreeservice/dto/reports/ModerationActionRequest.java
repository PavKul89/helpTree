package org.example.helptreeservice.dto.reports;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ModerationActionType;

import java.time.LocalDateTime;

/**
 * A moderation decision. Applying it always writes both a moderation action and
 * a report event in the same transaction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationActionRequest {

    @NotNull(message = "Тип действия обязателен")
    private ModerationActionType actionType;

    @NotNull(message = "Причина обязательна")
    @Size(min = 1, max = 1000, message = "Причина должна быть от 1 до 1000 символов")
    private String reason;

    @Size(max = 1000, message = "Решение не может быть длиннее 1000 символов")
    private String resolution;

    /** Used by LINK_DUPLICATE. */
    private Long duplicateOfId;

    /** Optional end of a temporary mute/ban/hide. */
    private LocalDateTime expiresAt;
}
