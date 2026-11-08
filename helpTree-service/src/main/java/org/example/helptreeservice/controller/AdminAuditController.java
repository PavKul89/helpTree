package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.admin.AdminAuditLogDto;
import org.example.helptreeservice.dto.admin.AdminAuditQuery;
import org.example.helptreeservice.dto.admin.AdminAuditStatsDto;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AdminAuditService;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.util.PageRequests;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Global administrative audit trail. Read-only by design: the table is
 * append-only and is written by {@link AdminAuditService} from the services
 * that perform the mutations.
 */
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@Tag(name = "Admin — Wallet & Audit", description = "Калибровка кошельков и аудит действий")
public class AdminAuditController {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "id", "createdAt", "adminId", "action", "entityType", "entityId");

    private final AuthorizationService authService;
    private final AdminAuditService adminAuditService;
    private final PaginationConfig paginationConfig;

    @GetMapping
    @Operation(summary = "Журнал административных действий (ADMIN, постранично)",
            description = "Фильтры по администратору, действию, сущности и датам; sort ограничен whitelist.")
    public ResponseEntity<PageResponse<AdminAuditLogDto>> search(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        requireAdmin();
        AdminAuditQuery query = buildQuery(adminId, action, entityType, entityId, from, to, q);
        return ResponseEntity.ok(PageResponse.from(
                adminAuditService.search(query, resolvePageable(page, size, sort, direction))));
    }

    @GetMapping("/stats")
    @Operation(summary = "Агрегированная статистика аудита (ADMIN)")
    public ResponseEntity<AdminAuditStatsDto> stats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        requireAdmin();
        return ResponseEntity.ok(adminAuditService.stats(from, to));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Экспорт аудита в CSV (ADMIN)",
            description = "Возвращает text/csv с BOM для корректного открытия в Excel.")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String q) {
        requireAdmin();
        AdminAuditQuery query = buildQuery(adminId, action, entityType, entityId, from, to, q);
        byte[] body = ("\uFEFF" + adminAuditService.exportCsv(query))
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"admin-audit.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private AdminAuditQuery buildQuery(Long adminId,
                                       String action,
                                       String entityType,
                                       Long entityId,
                                       LocalDateTime from,
                                       LocalDateTime to,
                                       String q) {
        AdminAuditQuery query = new AdminAuditQuery();
        query.setAdminId(adminId);
        query.setAction(action);
        query.setEntityType(entityType);
        query.setEntityId(entityId);
        query.setFrom(from);
        query.setTo(to);
        query.setQ(q);
        return query;
    }

    private Pageable resolvePageable(Integer page, Integer size, String sort, String direction) {
        String property = sort != null && ALLOWED_SORTS.contains(sort) ? sort : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort sortSpec = Sort.by(dir, property).and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequests.of(page, size, paginationConfig, sortSpec);
    }

    private void requireAdmin() {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может просматривать аудит");
        }
    }
}
