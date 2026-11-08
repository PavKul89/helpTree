package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.reports.CreateReportRequest;
import org.example.helptreeservice.dto.reports.ReportDetailsResponse;
import org.example.helptreeservice.dto.reports.ReportMetaResponse;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.ReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * User-facing report endpoints. A report is created here and then read by its
 * author; the moderation queue lives under {@code /api/admin/reports}.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Жалобы пользователей и модерация")
public class ReportController {

    private final ReportService reportService;
    private final AuthorizationService authService;
    private final PaginationConfig paginationConfig;

    @PostMapping
    @Operation(summary = "Подать жалобу",
            description = "Идемпотентно при наличии Idempotency-Key: повтор с тем же ключом не создаёт дубль.")
    public ResponseEntity<ReportResponse> create(
            @Valid @RequestBody CreateReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = requireUserId();
        ReportResponse response = reportService.createReport(request, userId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Мои жалобы (постранично)")
    public ResponseEntity<PageResponse<ReportResponse>> getMyReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) ReportReason reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = requireUserId();
        Page<ReportResponse> reports = reportService.getMyReports(
                userId, status, targetType, reason, resolvePageable(page, size));
        return ResponseEntity.ok(PageResponse.from(reports));
    }

    @GetMapping("/meta")
    @Operation(summary = "Справочник причин и типов жалоб")
    public ResponseEntity<ReportMetaResponse> getMeta() {
        requireUserId();
        return ResponseEntity.ok(reportService.getMeta());
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Детали своей жалобы")
    public ResponseEntity<ReportDetailsResponse> getMyReport(@PathVariable Long reportId) {
        Long userId = requireUserId();
        return ResponseEntity.ok(reportService.getMyReport(reportId, userId));
    }

    private Long requireUserId() {
        AuthorizationService.UserContext user = authService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
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
