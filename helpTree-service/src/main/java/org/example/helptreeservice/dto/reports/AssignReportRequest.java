package org.example.helptreeservice.dto.reports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Optional body for {@code POST /api/admin/reports/{id}/assign}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignReportRequest {

    /** Defaults to the current administrator when omitted. */
    private Long moderatorId;
}
