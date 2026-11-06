package org.example.helptreeservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.PublicRatingResponse;
import org.example.helptreeservice.dto.RatingResponse;
import org.example.helptreeservice.entity.RatingHistory;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.AuthorizationService.UserContext;
import org.example.helptreeservice.service.PrivacyPolicy;
import org.example.helptreeservice.service.RatingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
@Slf4j
public class RatingController {

    private final RatingService ratingService;
    private final AuthorizationService authService;
    private final PrivacyPolicy privacyPolicy;

    @GetMapping("/user/{userId}")
    public ResponseEntity<PublicRatingResponse> getUserRating(@PathVariable Long userId) {
        log.info("GET /api/ratings/user/{}", userId);
        return ResponseEntity.ok(PublicRatingResponse.from(ratingService.getUserRating(userId)));
    }

    @GetMapping("/top")
    public ResponseEntity<Page<PublicRatingResponse>> getTopRated(
            @PageableDefault(size = 20, sort = "currentRating", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /api/ratings/top, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(ratingService.getTopRatedUsers(pageable)
                .map(PublicRatingResponse::from));
    }

    @PostMapping("/user/{userId}/recalculate")
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
    public ResponseEntity<Page<RatingHistory>> getRatingHistory(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "calculatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (!privacyPolicy.canViewRatingHistory(userId)) {
            throw new ForbiddenException("История рейтинга доступна только владельцу профиля и администратору");
        }
        log.info("GET /api/ratings/user/{}/history", userId);
        return ResponseEntity.ok(ratingService.getRatingHistory(userId, pageable));
    }
}
