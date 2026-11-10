package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.reports.CreateReportRequest;
import org.example.helptreeservice.dto.reports.CreateReviewReportRequest;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.reviews.CreateReviewRequest;
import org.example.helptreeservice.dto.reviews.ReplyReviewRequest;
import org.example.helptreeservice.dto.reviews.ReviewResponse;
import org.example.helptreeservice.dto.reviews.UpdateReviewRequest;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.ReportService;
import org.example.helptreeservice.service.ReviewService;
import org.example.helptreeservice.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews & Ratings", description = "Отзывы и рейтинг доверия")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReportService reportService;
    private final AuthorizationService authService;
    private final PaginationConfig paginationConfig;

    @PostMapping
    @Operation(summary = "Оставить отзыв",
            description = "Только участник завершённой помощи; повторный отзыв на ту же помощь отклоняется 409.")
    public ResponseEntity<ReviewResponse> createReview(@Valid @RequestBody CreateReviewRequest request) {
        Long currentUserId = authService.getCurrentUser().getUserId();
        ReviewResponse response = reviewService.createReview(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/reply")
    @Operation(summary = "Ответить на отзыв",
            description = "Один ответ от получателя; изменить его можно в течение 24 часов.")
    public ResponseEntity<ReviewResponse> replyToReview(@PathVariable Long id,
                                                        @Valid @RequestBody ReplyReviewRequest request) {
        Long currentUserId = authService.getCurrentUser().getUserId();
        return ResponseEntity.ok(reviewService.replyToReview(id, request, currentUserId));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Отредактировать свой отзыв",
            description = "Доступно автору в течение 24 часов; рейтинг получателя пересчитывается.")
    public ResponseEntity<ReviewResponse> updateReview(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateReviewRequest request) {
        Long currentUserId = authService.getCurrentUser().getUserId();
        return ResponseEntity.ok(reviewService.updateReview(id, request, currentUserId));
    }

    @PostMapping("/{id}/report")
    @Operation(summary = "Пожаловаться на отзыв",
            description = "Создаёт жалобу типа REVIEW; идемпотентно при наличии Idempotency-Key.")
    public ResponseEntity<ReportResponse> reportReview(
            @PathVariable Long id,
            @Valid @RequestBody CreateReviewReportRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long currentUserId = authService.getCurrentUser().getUserId();
        CreateReportRequest reportRequest = new CreateReportRequest(
                ReportTargetType.REVIEW,
                id,
                request.getReason(),
                request.getDetails());
        ReportResponse response = reportService.createReport(reportRequest, currentUserId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/help/{helpId}")
    @Operation(summary = "Отзывы по помощи (постранично)")
    @SecurityRequirements
    public ResponseEntity<PageResponse<ReviewResponse>> getReviewsByHelp(
            @PathVariable Long helpId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Page<ReviewResponse> reviews = reviewService.getReviewsByHelp(helpId, responsePageable(page, size));
        return ResponseEntity.ok(PageResponse.from(reviews));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Отзывы о пользователе (постранично)")
    @SecurityRequirements
    public ResponseEntity<PageResponse<ReviewResponse>> getReviewsByUser(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Page<ReviewResponse> reviews = reviewService.getReviewsByUser(userId, responsePageable(page, size));
        return ResponseEntity.ok(PageResponse.from(reviews));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить свой отзыв")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        Long currentUserId = authService.getCurrentUser().getUserId();
        reviewService.deleteReview(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private Pageable responsePageable(Integer page, Integer size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequests.of(page, size, paginationConfig, sort);
    }
}
