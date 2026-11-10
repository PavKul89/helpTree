package org.example.helptreeservice.dto.reports;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ReportReason;

/**
 * Body for {@code POST /api/reviews/{id}/report}. The target is always the
 * review from the path, so the client cannot report a different object.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewReportRequest {

    @NotNull(message = "Причина жалобы обязательна")
    private ReportReason reason;

    @Size(max = 2000, message = "Описание не может быть длиннее 2000 символов")
    private String details;
}
