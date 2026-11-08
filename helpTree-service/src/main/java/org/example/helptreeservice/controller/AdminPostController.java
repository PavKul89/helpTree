package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.admin.AdminBulkPostsRequest;
import org.example.helptreeservice.dto.admin.AdminBulkResultDto;
import org.example.helptreeservice.dto.admin.AdminPostStatusRequest;
import org.example.helptreeservice.dto.admin.AdminReasonRequest;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.PostService;
import org.example.helptreeservice.util.PageRequests;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * Administrative request management: the deleted list, reasoned
 * delete/restore/status changes and per-item bulk operations. User-facing
 * {@code /api/posts} endpoints keep their existing ownership rules.
 */
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
@Tag(name = "Admin — Moderation", description = "Административные очереди и модерация")
public class AdminPostController {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "id", "title", "status", "createdAt", "updatedAt", "deletedAt");

    private final AuthorizationService authService;
    private final PostService postService;
    private final PaginationConfig paginationConfig;

    @GetMapping("/deleted")
    @Operation(summary = "Удалённые заявки (ADMIN, постранично)",
            description = "Фильтры q/category; sort ограничен whitelist.")
    public ResponseEntity<PageResponse<PostDto>> getDeleted(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "deletedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        requireAdmin();
        return ResponseEntity.ok(PageResponse.from(
                postService.getDeletedPosts(q, category, resolvePageable(page, size, sort, direction))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Мягко удалить заявку с причиной (ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam String reason) {
        Long adminId = requireAdmin();
        postService.deletePost(id, adminId, reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Восстановить удалённую заявку (ADMIN)")
    public ResponseEntity<Void> restore(@PathVariable Long id,
                                        @Valid @RequestBody AdminReasonRequest request) {
        Long adminId = requireAdmin();
        postService.restorePost(id, adminId, request.getReason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Сменить статус заявки (ADMIN)")
    public ResponseEntity<PostDto> changeStatus(@PathVariable Long id,
                                                @Valid @RequestBody AdminPostStatusRequest request) {
        Long adminId = requireAdmin();
        return ResponseEntity.ok(postService.changeStatus(
                id, parseStatus(request.getStatus()), adminId, request.getReason()));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Массовые операции над заявками (ADMIN)",
            description = "Частичный успех: результат содержит succeeded/failed по каждому id.")
    public ResponseEntity<AdminBulkResultDto> bulk(@Valid @RequestBody AdminBulkPostsRequest request) {
        Long adminId = requireAdmin();
        PostStatus status = request.getStatus() == null || request.getStatus().isBlank()
                ? null
                : parseStatus(request.getStatus());
        return ResponseEntity.ok(postService.bulk(
                request.getAction(), request.getIds(), status, adminId, request.getReason()));
    }

    private PostStatus parseStatus(String raw) {
        try {
            return PostStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Неизвестный статус заявки: " + raw);
        }
    }

    private Long requireAdmin() {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может управлять заявками");
        }
        AuthorizationService.UserContext current = authService.getCurrentUser();
        if (current == null || current.getUserId() == null) {
            throw new UnauthorizedException("Вы не авторизованы");
        }
        return current.getUserId();
    }

    private Pageable resolvePageable(Integer page, Integer size, String sort, String direction) {
        String property = sort != null && ALLOWED_SORTS.contains(sort) ? sort : "deletedAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort sortSpec = Sort.by(dir, property).and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequests.of(page, size, paginationConfig, sortSpec);
    }
}
