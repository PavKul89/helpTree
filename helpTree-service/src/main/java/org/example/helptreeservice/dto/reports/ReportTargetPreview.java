package org.example.helptreeservice.dto.reports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ReportTargetType;

import java.time.LocalDateTime;

/**
 * A safe read-only snapshot of the reported object. It never leaks content of a
 * closed object to a normal user: only administrators receive this response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportTargetPreview {

    private ReportTargetType targetType;
    private Long targetId;
    private boolean exists;
    private Long ownerId;
    private String ownerName;
    private String title;
    private String content;
    private String moderationStatus;
    private Boolean deleted;
    private LocalDateTime createdAt;
}
