package org.example.helptreeservice.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.admin.AdminAuditLogDto;
import org.example.helptreeservice.entity.AdminAuditLog;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.repository.AdminAuditLogRepository;
import org.example.helptreeservice.security.ClientInfoResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Single writer for the append-only administrative audit trail. Every critical
 * mutation records the acting admin, a mandatory reason, and the request
 * metadata needed for later investigation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    public static final String ENTITY_USER = "USER";

    private final AdminAuditLogRepository auditLogRepository;
    private final ClientInfoResolver clientInfoResolver;

    @Transactional
    public void record(Long adminId,
                       AdminAuditAction action,
                       String entityType,
                       Long entityId,
                       Map<String, Object> beforeState,
                       Map<String, Object> afterState,
                       String reason) {
        if (action == null || entityType == null || entityType.isBlank()) {
            return;
        }

        HttpServletRequest request = currentRequest();
        AdminAuditLog entry = AdminAuditLog.builder()
                .adminId(adminId)
                .action(action.name())
                .entityType(entityType)
                .entityId(entityId)
                .beforeState(beforeState)
                .afterState(afterState)
                .reason(truncate(reason, 1000))
                .ipAddress(request != null ? truncate(clientInfoResolver.resolveIp(request), 64) : null)
                .userAgent(request != null ? truncate(clientInfoResolver.resolveUserAgent(request), 512) : null)
                .requestId(request != null ? truncate(request.getHeader("X-Request-Id"), 100) : null)
                .build();
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLogDto> history(String entityType, Long entityId, Pageable pageable) {
        return auditLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable)
                .map(entry -> AdminAuditLogDto.builder()
                        .id(entry.getId())
                        .adminId(entry.getAdminId())
                        .action(entry.getAction())
                        .entityType(entry.getEntityType())
                        .entityId(entry.getEntityId())
                        .beforeState(entry.getBeforeState())
                        .afterState(entry.getAfterState())
                        .reason(entry.getReason())
                        .ipAddress(entry.getIpAddress())
                        .userAgent(entry.getUserAgent())
                        .requestId(entry.getRequestId())
                        .createdAt(entry.getCreatedAt())
                        .build());
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
