package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.comments.CommentActionResponse;
import org.example.helptreeservice.dto.comments.CommentPageResponse;
import org.example.helptreeservice.dto.comments.CommentResponse;
import org.example.helptreeservice.dto.comments.CreateCommentRequest;
import org.example.helptreeservice.dto.reports.CreateCommentReportRequest;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.enums.CommentSort;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.CommentService;
import org.example.helptreeservice.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Comments", description = "Комментарии к заявкам")
public class CommentController {

    private final CommentService commentService;
    private final ReportService reportService;
    private final AuthorizationService authorizationService;

    @PostMapping
    @Operation(summary = "Добавить комментарий к заявке")
    public ResponseEntity<CommentActionResponse> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request) {
        Long userId = requireUserId();
        CommentActionResponse response = commentService.createComment(postId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Комментарии к заявке (постранично)",
            description = "Собственный контракт CommentPageResponse с полем content и счётчиками.")
    @SecurityRequirements
    public ResponseEntity<CommentPageResponse> getComments(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "OLDEST") CommentSort sort) {
        return ResponseEntity.ok(commentService.getCommentsByPost(postId, page, size, sort));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Удалить комментарий",
            description = "Автор или ADMIN. Идемпотентное представление результата.")
    public ResponseEntity<CommentActionResponse> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        Long userId = requireUserId();
        CommentActionResponse response = commentService.deleteComment(postId, commentId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{commentId}/reports")
    @Operation(summary = "Пожаловаться на комментарий")
    public ResponseEntity<ReportResponse> reportComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody CreateCommentReportRequest request) {
        Long userId = requireUserId();
        ReportResponse response = reportService.createCommentReport(postId, commentId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Long requireUserId() {
        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        return user.getUserId();
    }
}
