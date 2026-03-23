package org.example.helptreeservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.dto.reviews.CreateReviewRequest;
import org.example.helptreeservice.dto.reviews.ReviewResponse;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final AuthorizationService authService;

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(@Valid @RequestBody CreateReviewRequest request) {
        Long currentUserId = authService.getCurrentUser().getUserId();
        ReviewResponse response = reviewService.createReview(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/help/{helpId}")
    public ResponseEntity<List<ReviewResponse>> getReviewsByHelp(@PathVariable Long helpId) {
        return ResponseEntity.ok(reviewService.getReviewsByHelp(helpId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReviewResponse>> getReviewsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(reviewService.getReviewsByUser(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        Long currentUserId = authService.getCurrentUser().getUserId();
        reviewService.deleteReview(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
