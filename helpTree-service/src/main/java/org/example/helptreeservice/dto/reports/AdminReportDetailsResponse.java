package org.example.helptreeservice.dto.reports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Full administrative view: the report, its history, actions and target. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminReportDetailsResponse {

    private AdminReportResponse report;
    private ReportTargetPreview target;
    private List<ReportEventResponse> events;
    private List<ModerationActionResponse> actions;
}
