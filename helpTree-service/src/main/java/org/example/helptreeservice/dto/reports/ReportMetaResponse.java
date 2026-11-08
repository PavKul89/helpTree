package org.example.helptreeservice.dto.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ReportPriority;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;

import java.util.List;

/** Server-provided catalogue for the report form. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "ReportMetaResponse", description = "Server-provided catalogue for the report form")
public class ReportMetaResponse {

    private List<ReportTargetType> targetTypes;
    private List<ReportReason> reasons;
    private List<ReportStatus> statuses;
    private List<ReportPriority> priorities;
    private int maxDescriptionLength;
}
