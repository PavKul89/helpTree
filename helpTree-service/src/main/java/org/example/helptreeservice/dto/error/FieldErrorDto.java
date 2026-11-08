package org.example.helptreeservice.dto.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One field-level validation failure. The list is ordered and may contain more
 * than one entry per field (for example a length and a format violation).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "FieldError", description = "A single field-level validation failure")
public record FieldErrorDto(
        @Schema(description = "Request field or query parameter name", example = "email",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String field,
        @Schema(description = "Human-readable explanation", example = "Некорректный адрес электронной почты",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(description = "Stable validation code when available", example = "Email")
        String code) {
}
