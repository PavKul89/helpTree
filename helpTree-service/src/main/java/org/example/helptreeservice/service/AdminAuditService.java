package org.example.helptreeservice.service;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.admin.AdminAuditLogDto;
import org.example.helptreeservice.dto.admin.AdminAuditQuery;
import org.example.helptreeservice.dto.admin.AdminAuditStatsDto;
import org.example.helptreeservice.entity.AdminAuditLog;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.repository.AdminAuditLogRepository;
import org.example.helptreeservice.security.ClientInfoResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single writer and single reader for the append-only administrative audit
 * trail. Every critical mutation records the acting admin, a mandatory reason,
 * and the request metadata needed for later investigation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    public static final String ENTITY_USER = "USER";
    public static final String ENTITY_POST = "POST";
    public static final String ENTITY_REVIEW = "REVIEW";
    public static final String ENTITY_REPORT = "REPORT";
    public static final String ENTITY_WALLET = "WALLET";
    public static final String ENTITY_SESSION = "SESSION";
    public static final String ENTITY_HELP = "HELP";
    public static final String ENTITY_MODERATION = "MODERATION";

    private static final int CSV_MAX_ROWS = 50_000;

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
                .map(this::toDto);
    }

    /**
     * Global audit search. All filters are optional and applied in the
     * database, so the admin page never loads the whole table.
     */
    @Transactional(readOnly = true)
    public Page<AdminAuditLogDto> search(AdminAuditQuery query, Pageable pageable) {
        return auditLogRepository.findAll(specification(query), pageable).map(this::toDto);
    }

    /**
     * Aggregated activity for a bounded window. The caller always supplies the
     * window, which keeps the native aggregate queries type-safe.
     */
    @Transactional(readOnly = true)
    public AdminAuditStatsDto stats(LocalDateTime from, LocalDateTime to) {
        LocalDateTime resolvedTo = to != null ? to : LocalDateTime.now();
        LocalDateTime resolvedFrom = from != null ? from : resolvedTo.minusDays(30);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new org.example.helptreeservice.exception.BadRequestException(
                    "Начало периода не может быть позже окончания");
        }

        Map<String, Long> byAction = toStringMap(auditLogRepository.countByAction(resolvedFrom, resolvedTo));
        Map<String, Long> byEntity = toStringMap(
                auditLogRepository.countByEntityType(resolvedFrom, resolvedTo));
        Map<String, Long> byAdmin = toStringMap(
                auditLogRepository.countByAdmin(resolvedFrom, resolvedTo));

        return AdminAuditStatsDto.builder()
                .total(auditLogRepository.countInWindow(resolvedFrom, resolvedTo))
                .from(resolvedFrom)
                .to(resolvedTo)
                .byAction(byAction)
                .byEntityType(byEntity)
                .byAdmin(byAdmin)
                .build();
    }

    /**
     * CSV export of the filtered trail. It is a sensitive read and is therefore
     * authorised by the caller; the row cap prevents an accidental unbounded
     * download.
     */
    @Transactional(readOnly = true)
    public String exportCsv(AdminAuditQuery query) {
        Pageable pageable = PageRequest.of(0, CSV_MAX_ROWS,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        List<AdminAuditLog> rows = auditLogRepository.findAll(specification(query), pageable)
                .getContent();

        StringBuilder csv = new StringBuilder();
        csv.append("id,createdAt,adminId,action,entityType,entityId,reason,ipAddress,requestId,beforeState,afterState\n");
        for (AdminAuditLog row : rows) {
            appendCsv(csv, row.getId());
            appendCsv(csv, row.getCreatedAt());
            appendCsv(csv, row.getAdminId());
            appendCsv(csv, row.getAction());
            appendCsv(csv, row.getEntityType());
            appendCsv(csv, row.getEntityId());
            appendCsv(csv, row.getReason());
            appendCsv(csv, row.getIpAddress());
            appendCsv(csv, row.getRequestId());
            appendCsv(csv, row.getBeforeState());
            appendCsv(csv, row.getAfterState());
            csv.append('\n');
        }
        return csv.toString();
    }

    private Specification<AdminAuditLog> specification(AdminAuditQuery query) {
        AdminAuditQuery filters = query != null ? query : new AdminAuditQuery();
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filters.getAdminId() != null) {
                predicates.add(cb.equal(root.get("adminId"), filters.getAdminId()));
            }
            if (StringUtils.hasText(filters.getAction())) {
                predicates.add(cb.equal(root.get("action"),
                        filters.getAction().trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(filters.getEntityType())) {
                predicates.add(cb.equal(root.get("entityType"),
                        filters.getEntityType().trim().toUpperCase(Locale.ROOT)));
            }
            if (filters.getEntityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), filters.getEntityId()));
            }
            if (filters.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filters.getFrom()));
            }
            if (filters.getTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filters.getTo()));
            }
            if (StringUtils.hasText(filters.getQ())) {
                String pattern = "%" + filters.getQ().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("reason")), pattern),
                        cb.like(cb.lower(root.get("action")), pattern),
                        cb.like(cb.lower(root.get("entityType")), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AdminAuditLogDto toDto(AdminAuditLog entry) {
        return AdminAuditLogDto.builder()
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
                .build();
    }

    private Map<String, Long> toStringMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Object key = row[0];
            Object value = row[1];
            if (key == null) {
                continue;
            }
            result.put(String.valueOf(key), value instanceof Number number ? number.longValue() : 0L);
        }
        return result;
    }

    private void appendCsv(StringBuilder csv, Object value) {
        if (value == null) {
            csv.append(',');
            return;
        }
        String text = String.valueOf(value);
        csv.append('"').append(text.replace("\"", "\"\"")).append('"').append(',');
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
