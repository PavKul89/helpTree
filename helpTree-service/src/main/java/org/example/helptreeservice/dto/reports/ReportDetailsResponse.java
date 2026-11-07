package org.example.helptreeservice.dto.reports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A user's own report with its public status history. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportDetailsResponse {

    private ReportResponse report;
    private List<ReportEventResponse> events;
}
