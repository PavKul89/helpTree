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
 * Administrative queue projection. Unlike the user DTO this includes the
 * reporter's email and the staff-only internal note.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminReportResponse {

    private Long id;
    private Long reporterId;
    private String reporterName;
    private String reporterEmail;
    private ReportTargetType targetType;
    private Long targetId;
    private ReportReason reason;
    private String description;
    private ReportStatus status;
    private ReportPriority priority;
    private Long assignedModeratorId;
    private String assignedModeratorName;
    private String resolution;
    private String internalNote;
    private Long duplicateOfId;
    private Long resolvedById;
    private String resolvedByName;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
