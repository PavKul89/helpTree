package org.example.helptreeservice.dto.reports;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ReportAction;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolveReportRequest {

    @NotNull(message = "Действие модератора обязательно")
    private ReportAction action;

    @Size(max = 1000, message = "Резолюция не может быть длиннее 1000 символов")
    private String resolution;
}
