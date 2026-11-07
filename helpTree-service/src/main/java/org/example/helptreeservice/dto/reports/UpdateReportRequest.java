package org.example.helptreeservice.dto.reports;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ReportPriority;

/**
 * Metadata-only administrative update. Status transitions are deliberately not
 * accepted here: they must go through {@code /assign} or {@code /actions} so
 * every decision is written to {@code report_events}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReportRequest {

    private ReportPriority priority;

    @Size(max = 4000, message = "Внутренняя заметка не может быть длиннее 4000 символов")
    private String internalNote;
}
