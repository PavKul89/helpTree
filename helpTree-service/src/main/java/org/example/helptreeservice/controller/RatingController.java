package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.PublicRatingResponse;
import org.example.helptreeservice.dto.RatingResponse;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.entity.RatingHistory;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.AuthorizationService.UserContext;
import org.example.helptreeservice.service.PrivacyPolicy;
import org.example.helptreeservice.service.RatingService;
import org.example.helptreeservice.util.PageRequests;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reviews & Ratings", description = "Отзывы и рейтинг доверия")
public class RatingController {

    private final RatingService ratingService;
    private final AuthorizationService authService;
    private final PrivacyPolicy privacyPolicy;
    private final PaginationConfig paginationConfig;

    @GetMapping("/user/{userId}")
    @Operation(summary = "Публичный рейтинг пользователя")
    @SecurityRequirements
    public ResponseEntity<PublicRatingResponse> getUserRating(@PathVariable Long userId) {
        log.info("GET /api/ratings/user/{}", userId);
        return ResponseEntity.ok(PublicRatingResponse.from(ratingService.getUserRating(userId)));
    }

    @GetMapping("/top")
    @Operation(summary = "Топ пользователей по рейтингу (постранично)")
    @SecurityRequirements
    public ResponseEntity<PageResponse<PublicRatingResponse>> getTopRated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequests.of(page, size, paginationConfig,
                Sort.by(Sort.Direction.DESC, "currentRating"));
        log.info("GET /api/ratings/top, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(PageResponse.from(
                ratingService.getTopRatedUsers(pageable).map(PublicRatingResponse::from)));
    }

    @PostMapping("/user/{userId}/recalculate")
    @Operation(summary = "Пересчитать рейтинг пользователя (ADMIN)")
    public ResponseEntity<RatingResponse> recalculateUserRating(@PathVariable Long userId) {
        UserContext user = authService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        if (!"ADMIN".equals(user.getRole())) {
            throw new ForbiddenException("Только администратор может пересчитывать рейтинг");
        }
        log.info("POST /api/ratings/user/{}/recalculate (by admin {})", userId, user.getUserId());
        return ResponseEntity.ok(ratingService.recalculateUserRating(userId));
    }

    @GetMapping("/user/{userId}/history")
    @Operation(summary = "История расчётов рейтинга",
            description = "Только владелец профиля и ADMIN; для остальных 403 даже при наличии токена.")
    public ResponseEntity<PageResponse<RatingHistory>> getRatingHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!privacyPolicy.canViewRatingHistory(userId)) {
            throw new ForbiddenException("История рейтинга доступна только владельцу профиля и администратору");
        }
        Pageable pageable = PageRequests.of(page, size, paginationConfig,
                Sort.by(Sort.Direction.DESC, "calculatedAt"));
        log.info("GET /api/ratings/user/{}/history", userId);
        return ResponseEntity.ok(PageResponse.from(
                ratingService.getRatingHistory(userId, pageable)));
    }
}
