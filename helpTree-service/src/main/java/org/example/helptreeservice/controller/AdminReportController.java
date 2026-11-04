package org.example.helptreeservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.reports.ResolveReportRequest;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.ReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Single server-side entry point for the moderation queue. Reports are created
 * by users through the comment report endpoint and resolved here.
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AuthorizationService authService;
    private final ReportService reportService;
    private final PaginationConfig paginationConfig;

    @GetMapping
    public ResponseEntity<Page<ReportResponse>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        return ResponseEntity.ok(reportService.getReports(status, targetType, resolvePageable(page, size)));
    }

    @PostMapping("/{reportId}/resolve")
    public ResponseEntity<ReportResponse> resolveReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ResolveReportRequest request) {
        requireAdmin();
        Long adminId = authService.getCurrentUser().getUserId();
        return ResponseEntity.ok(reportService.resolveReport(reportId, request, adminId));
    }

    private void requireAdmin() {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может просматривать жалобы");
        }
    }

    private Pageable resolvePageable(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Параметр page не может быть отрицательным");
        }
        if (size < 1) {
            throw new BadRequestException("Параметр size должен быть положительным");
        }
        int resolvedSize = Math.min(size, paginationConfig.getMaxPageSize());
        return PageRequest.of(page, resolvedSize, Sort.by(Sort.Direction.ASC, "createdAt"));
    }
}
