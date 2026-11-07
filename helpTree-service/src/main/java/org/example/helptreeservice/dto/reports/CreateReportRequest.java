package org.example.helptreeservice.dto.reports;

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
public class CreateReportRequest {

    @NotNull(message = "Тип объекта обязателен")
    private ReportTargetType targetType;

    @NotNull(message = "ID объекта обязателен")
    @Positive(message = "ID объекта должен быть положительным")
    private Long targetId;

    @NotNull(message = "Причина жалобы обязательна")
    private ReportReason reason;

    @Size(max = 2000, message = "Описание не может быть длиннее 2000 символов")
    private String description;
}
