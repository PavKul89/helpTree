package org.example.helptreeservice.dto.reports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ModerationActionType;
import org.example.helptreeservice.enums.ReportTargetType;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationActionResponse {

    private Long id;
    private Long reportId;
    private Long moderatorId;
    private String moderatorName;
    private ModerationActionType actionType;
    private ReportTargetType targetType;
    private Long targetId;
    private String reason;
    private LocalDateTime expiresAt;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
