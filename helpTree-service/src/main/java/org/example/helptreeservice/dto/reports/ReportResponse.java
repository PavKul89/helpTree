package org.example.helptreeservice.dto.reports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ReportPriority;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;

import java.time.LocalDateTime;

/**
 * User-facing report projection. It deliberately has no internal_note field.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResponse {

    private Long id;
    private Long reporterId;
    private String reporterName;
    private ReportTargetType targetType;
    private Long targetId;
    private ReportReason reason;
    private String description;
    private ReportStatus status;
    private ReportPriority priority;
    private Long assignedModeratorId;
    private String assignedModeratorName;
    private String resolution;
    private Long duplicateOfId;
    private Long resolvedById;
    private String resolvedByName;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
