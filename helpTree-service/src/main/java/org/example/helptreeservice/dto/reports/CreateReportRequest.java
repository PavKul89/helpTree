package org.example.helptreeservice.dto.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportTargetType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateReportRequest", description = "New report payload; target must be visible to the reporter")
public class CreateReportRequest {

    @NotNull(message = "Тип объекта обязателен")
    @Schema(description = "Reported entity type", example = "POST")
    private ReportTargetType targetType;

    @NotNull(message = "ID объекта обязателен")
    @Positive(message = "ID объекта должен быть положительным")
    @Schema(description = "Reported entity id", example = "42")
    private Long targetId;

    @NotNull(message = "Причина жалобы обязательна")
    @Schema(description = "Reason code", example = "SPAM")
    private ReportReason reason;

    @Size(max = 2000, message = "Описание не может быть длиннее 2000 символов")
    @Schema(description = "Optional free-form description", example = "Повторяющиеся рекламные сообщения")
    private String description;
}
