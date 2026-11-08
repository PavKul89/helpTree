package org.example.helptreeservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.admin.AdminAnonymizeRequest;
import org.example.helptreeservice.dto.admin.AdminAuditLogDto;
import org.example.helptreeservice.dto.admin.AdminBulkResultDto;
import org.example.helptreeservice.dto.admin.AdminBulkUsersRequest;
import org.example.helptreeservice.dto.admin.AdminCreateUserRequest;
import org.example.helptreeservice.dto.admin.AdminInviteUserRequest;
import org.example.helptreeservice.dto.admin.AdminRatingRequest;
import org.example.helptreeservice.dto.admin.AdminReasonRequest;
import org.example.helptreeservice.dto.admin.AdminRoleChangeRequest;
import org.example.helptreeservice.dto.admin.AdminSessionDto;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AdminUserService;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.util.PageRequests;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side entry point for administrative user management.
 *
 * <p>Every method performs an explicit {@code ADMIN} check in addition to the
 * {@code /api/admin/**} security rule. The old {@code /api/users} endpoints keep
 * working for backwards compatibility, but the new interface is the only place
 * that exposes deleted accounts, anonymization, exports and the audit trail.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "id", "name", "email", "rating", "helpedCount", "status", "createdAt", "deletedAt");

    private final AdminUserService adminUserService;
    private final AuthorizationService authService;
    private final PaginationConfig paginationConfig;

    @GetMapping
    public ResponseEntity<PageResponse<UserDto>> search(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        requireAdmin();
        Pageable pageable = resolvePageable(page, size, sort, direction);
        return ResponseEntity.ok(PageResponse.from(
                adminUserService.search(pageable, q, role, status, blocked, deleted)));
    }

    /**
     * Convenience view of soft-deleted accounts, including anonymized rows so an
     * administrator can see that the account exists and cannot be restored.
     */
    @GetMapping("/deleted")
    public ResponseEntity<PageResponse<UserDto>> getDeleted(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "deletedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        requireAdmin();
        Pageable pageable = resolvePageable(page, size, sort, direction);
        return ResponseEntity.ok(PageResponse.from(
                adminUserService.search(pageable, q, role, status, null, true)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> get(@PathVariable Long id) {
        requireAdmin();
        return ResponseEntity.ok(adminUserService.get(id));
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody AdminCreateUserRequest request) {
        Long adminId = requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.createWithPassword(request, adminId));
    }

    @PostMapping("/invite")
    public ResponseEntity<UserDto> invite(@Valid @RequestBody AdminInviteUserRequest request) {
        Long adminId = requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.invite(request, adminId));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UserDto> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody AdminRoleChangeRequest request) {
        Long adminId = requireAdmin();
        return ResponseEntity.ok(
                adminUserService.changeRole(id, request.getRole(), request.getReason(), adminId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam String reason) {
        Long adminId = requireAdmin();
        adminUserService.delete(id, reason, adminId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(
            @PathVariable Long id,
            @Valid @RequestBody AdminReasonRequest request) {
        Long adminId = requireAdmin();
        adminUserService.restore(id, request.getReason(), adminId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/anonymize")
    public ResponseEntity<Void> anonymize(
            @PathVariable Long id,
            @Valid @RequestBody AdminAnonymizeRequest request) {
        Long adminId = requireAdmin();
        adminUserService.anonymize(id, request.getReason(), request.getConfirmEmail(), adminId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/rating")
    public ResponseEntity<Void> changeRating(@PathVariable Long id,
                                             @Valid @RequestBody AdminRatingRequest request) {
        Long adminId = requireAdmin();
        adminUserService.changeRating(id, request.getRating(), request.getReason(), adminId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/sessions")
    public ResponseEntity<List<AdminSessionDto>> sessions(@PathVariable Long id) {
        requireAdmin();
        return ResponseEntity.ok(adminUserService.sessions(id));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable Long id,
                                              @PathVariable UUID sessionId,
                                              @RequestParam String reason) {
        Long adminId = requireAdmin();
        adminUserService.revokeSession(id, sessionId, reason, adminId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/sessions")
    public ResponseEntity<Void> revokeAllSessions(@PathVariable Long id,
                                                  @RequestParam String reason) {
        Long adminId = requireAdmin();
        adminUserService.revokeAllSessions(id, reason, adminId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    public ResponseEntity<AdminBulkResultDto> bulk(
            @Valid @RequestBody AdminBulkUsersRequest request) {
        Long adminId = requireAdmin();
        return ResponseEntity.ok(adminUserService.bulk(request, adminId));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<Map<String, Object>> export(
            @PathVariable Long id,
            @RequestParam String reason) {
        Long adminId = requireAdmin();
        return ResponseEntity.ok(adminUserService.export(id, reason, adminId));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<PageResponse<AdminAuditLogDto>> auditHistory(
            @PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        requireAdmin();
        Pageable pageable = resolvePageable(page, size, "createdAt", "desc");
        return ResponseEntity.ok(PageResponse.from(adminUserService.auditHistory(id, pageable)));
    }

    private Long requireAdmin() {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может управлять пользователями");
        }
        AuthorizationService.UserContext current = authService.getCurrentUser();
        if (current == null || current.getUserId() == null) {
            throw new UnauthorizedException("Вы не авторизованы");
        }
        return current.getUserId();
    }

    /**
     * Server-side sort whitelist. The id tiebreaker keeps deep pages stable.
     */
    private Pageable resolvePageable(Integer page, Integer size, String sort, String direction) {
        String property = sort != null && ALLOWED_SORTS.contains(sort) ? sort : "id";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort sortSpec = Sort.by(dir, property).and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequests.of(page, size, paginationConfig, sortSpec);
    }
}
