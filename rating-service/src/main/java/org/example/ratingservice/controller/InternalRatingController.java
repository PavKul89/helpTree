package org.example.ratingservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ratingservice.service.RatingSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/ratings")
@RequiredArgsConstructor
@Slf4j
public class InternalRatingController {

    private final RatingSyncService syncService;

    @PostMapping("/sync/{userId}")
    public ResponseEntity<String> syncUserRating(@PathVariable Long userId) {
        log.info("Синхронизация рейтинга для пользователя ID: {}", userId);
        syncService.syncUserRating(userId);
        return ResponseEntity.ok("Рейтинг синхронизирован");
    }

    @PostMapping("/sync-all")
    public ResponseEntity<String> syncAllRatings() {
        log.info("Синхронизация всех рейтингов");
        syncService.syncAllRatings();
        return ResponseEntity.ok("Все рейтинги синхронизированы");
    }
}
