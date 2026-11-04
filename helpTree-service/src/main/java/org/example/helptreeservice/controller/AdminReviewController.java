package org.example.helptreeservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.reviews.ReviewResponse;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Global review moderation list.
 *
 * <p>Reviews used to be reachable only per user or per help, which made
 * moderation impractical. This endpoint is the single server-side source for
 * the admin review page; filters and pagination are applied in the database.</p>
 */
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AuthorizationService authService;
    private final ReviewService reviewService;
    private final PaginationConfig paginationConfig;

    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getReviews(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin();

        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BadRequestException("Параметр rating должен быть от 1 до 5");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Параметр from не может быть позже параметра to");
        }

        Pageable pageable = resolvePageable(page, size);
        return ResponseEntity.ok(reviewService.getAdminReviews(userId, rating, from, to, pageable));
    }

    private void requireAdmin() {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может просматривать все отзывы");
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
        return PageRequest.of(page, resolvedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
