package org.example.helptreeservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.RatingResponse;
import org.example.helptreeservice.entity.RatingHistory;
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

    @GetMapping("/user/{userId}")
    public ResponseEntity<RatingResponse> getUserRating(@PathVariable Long userId) {
        log.info("GET /api/ratings/user/{}", userId);
        return ResponseEntity.ok(ratingService.getUserRating(userId));
    }

    @GetMapping("/top")
    public ResponseEntity<Page<RatingResponse>> getTopRated(
            @PageableDefault(size = 20, sort = "currentRating", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /api/ratings/top, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(ratingService.getTopRatedUsers(pageable));
    }

    @PostMapping("/user/{userId}/recalculate")
    public ResponseEntity<RatingResponse> recalculateUserRating(@PathVariable Long userId) {
        log.info("POST /api/ratings/user/{}/recalculate", userId);
        return ResponseEntity.ok(ratingService.recalculateUserRating(userId));
    }

    @GetMapping("/user/{userId}/history")
    public ResponseEntity<Page<RatingHistory>> getRatingHistory(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "calculatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /api/ratings/user/{}/history", userId);
        return ResponseEntity.ok(ratingService.getRatingHistory(userId, pageable));
    }
}
