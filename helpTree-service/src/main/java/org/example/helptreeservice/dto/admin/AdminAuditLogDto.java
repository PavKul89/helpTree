package org.example.helptreeservice.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class AdminAuditLogDto {
    private Long id;
    private Long adminId;
    private String action;
    private String entityType;
    private Long entityId;
    private Map<String, Object> beforeState;
    private Map<String, Object> afterState;
    private String reason;
    private String ipAddress;
    private String userAgent;
    private String requestId;
    private LocalDateTime createdAt;
}
