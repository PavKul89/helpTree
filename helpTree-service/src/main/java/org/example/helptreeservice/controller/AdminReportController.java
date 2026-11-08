package org.example.helptreeservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.reports.AdminReportDetailsResponse;
import org.example.helptreeservice.dto.reports.AdminReportResponse;
import org.example.helptreeservice.dto.reports.AssignReportRequest;
import org.example.helptreeservice.dto.reports.ModerationActionRequest;
import org.example.helptreeservice.dto.reports.ModerationActionResponse;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.reports.ResolveReportRequest;
import org.example.helptreeservice.dto.reports.UpdateReportRequest;
import org.example.helptreeservice.enums.ReportPriority;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.ReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Administrative moderation queue.
 *
 * <p>Status changes are intentionally split from metadata updates:</p>
 * <ul>
 *   <li>{@code PATCH /{id}} changes priority and the staff-only note;</li>
 *   <li>{@code POST /{id}/assign} assigns a moderator and moves the report to
 *       {@code IN_REVIEW};</li>
 *   <li>{@code POST /{id}/actions} applies a decision/sanction and closes the
 *       report.</li>
 * </ul>
 *
 * <p>The legacy {@code POST /{id}/resolve} endpoint is kept as a thin wrapper
 * over the same action pipeline.</p>
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AuthorizationService authService;
    private final ReportService reportService;
    private final PaginationConfig paginationConfig;

    @GetMapping
    public ResponseEntity<PageResponse<AdminReportResponse>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) ReportReason reason,
            @RequestParam(required = false) ReportPriority priority,
            @RequestParam(required = false) Long assignedModeratorId,
            @RequestParam(required = false) Long reporterId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        Page<AdminReportResponse> reports = reportService.getReports(
                status, targetType, reason, priority, assignedModeratorId, reporterId,
                from, to, q, resolvePageable(page, size));
        return ResponseEntity.ok(PageResponse.from(reports));
    }

    @GetMapping("/sla")
    public ResponseEntity<org.example.helptreeservice.dto.reports.ModerationSlaDto> getSla(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        requireAdmin();
        return ResponseEntity.ok(reportService.getModerationSla(from, to));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<AdminReportDetailsResponse> getReport(@PathVariable Long reportId) {
        requireAdmin();
        return ResponseEntity.ok(reportService.getAdminReport(reportId));
    }

    @PatchMapping("/{reportId}")
    public ResponseEntity<AdminReportResponse> updateReport(
            @PathVariable Long reportId,
            @Valid @RequestBody UpdateReportRequest request) {
        Long adminId = requireAdmin();
        return ResponseEntity.ok(reportService.updateReport(reportId, request, adminId));
    }

    @PostMapping("/{reportId}/assign")
    public ResponseEntity<AdminReportResponse> assignReport(
            @PathVariable Long reportId,
            @RequestBody(required = false) AssignReportRequest request) {
        Long adminId = requireAdmin();
        return ResponseEntity.ok(reportService.assignReport(reportId, request, adminId));
    }

    @PostMapping("/{reportId}/actions")
    public ResponseEntity<ModerationActionResponse> applyAction(
            @PathVariable Long reportId,
            @Valid @RequestBody ModerationActionRequest request) {
        Long adminId = requireAdmin();
        return ResponseEntity.ok(reportService.applyAction(reportId, request, adminId));
    }

    /**
     * Backwards-compatible endpoint. New clients must use
     * {@code POST /api/admin/reports/{id}/actions}.
     */
    @PostMapping("/{reportId}/resolve")
    public ResponseEntity<ReportResponse> resolveReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ResolveReportRequest request) {
        Long adminId = requireAdmin();
        return ResponseEntity.ok(reportService.resolveReport(reportId, request, adminId));
    }

    private Long requireAdmin() {
        AuthorizationService.UserContext user = authService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        if (!"ADMIN".equals(user.getRole())) {
            throw new ForbiddenException("Только администратор может рассматривать жалобы");
        }
        return user.getUserId();
    }

    private Pageable resolvePageable(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Параметр page не может быть отрицательным");
        }
        if (size < 1) {
            throw new BadRequestException("Параметр size должен быть положительным");
        }
        return PageRequest.of(page, Math.min(size, paginationConfig.getMaxPageSize()));
    }
}
