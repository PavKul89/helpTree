package org.example.helptreeservice.dto.reports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** Moderation SLA dashboard projection. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationSlaDto {
    private LocalDateTime from;
    private LocalDateTime to;
    private long openReports;
    private long inReviewReports;
    private long resolvedReports;
    private long rejectedReports;
    private long totalReports;
    private Map<String, Long> openByPriority;
    private Double averageResolutionHours;
    private Double averageFirstResponseHours;
    private LocalDateTime oldestOpenCreatedAt;
    private Double oldestOpenAgeHours;
}
