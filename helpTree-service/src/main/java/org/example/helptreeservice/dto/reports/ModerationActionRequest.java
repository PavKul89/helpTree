package org.example.helptreeservice.dto.reports;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "ModerationActionRequest",
        description = "A moderation decision; applying it writes an action and a report event atomically")
public class ModerationActionRequest {

    @NotNull(message = "Тип действия обязателен")
    @Schema(description = "Sanction type", example = "MUTE")
    private ModerationActionType actionType;

    @NotNull(message = "Причина обязательна")
    @Size(min = 1, max = 1000, message = "Причина должна быть от 1 до 1000 символов")
    @Schema(description = "Administrative reason", example = "Оскорбления в комментариях")
    private String reason;

    @Size(max = 1000, message = "Решение не может быть длиннее 1000 символов")
    @Schema(description = "Optional resolution text shown to the reporter")
    private String resolution;

    /** Used by LINK_DUPLICATE. */
    private Long duplicateOfId;

    /** Optional end of a temporary mute/ban/hide. */
    private LocalDateTime expiresAt;
}
