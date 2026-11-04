package org.example.helptreeservice.dto.reports;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ReportReason;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentReportRequest {

    @NotNull(message = "Причина жалобы обязательна")
    private ReportReason reason;

    @Size(max = 2000, message = "Детали жалобы не могут быть длиннее 2000 символов")
    private String details;
}
