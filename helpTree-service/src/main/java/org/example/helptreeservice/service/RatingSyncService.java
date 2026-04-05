package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.entity.UserRatingStats;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingSyncService {

    private final UserRatingStatsRepository statsRepository;
    private final RatingCalculationService calculationService;

    public void syncUserRating(Long userId) {
        log.info("Синхронизация рейтинга для пользователя ID: {}", userId);
        
        UserRatingStats stats = statsRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserRatingStats newStats = UserRatingStats.builder()
                            .userId(userId)
                            .totalHelpsGiven(0L)
                            .totalHelpsReceived(0L)
                            .successfulHelps(0L)
                            .cancelledHelps(0L)
                            .currentRating(3.0)
                            .ratingTrend("STABLE")
                            .lastCalculated(LocalDateTime.now())
                            .build();
                    return statsRepository.save(newStats);
                });
        
        calculationService.calculateUserRating(userId);
        log.info("Синхронизация завершена для пользователя ID: {}", userId);
    }

    public void recalculateAllRatings() {
        log.info("Запуск полной синхронизации всех рейтингов");
        
        statsRepository.findAll().forEach(stats -> {
            try {
                calculationService.calculateUserRating(stats.getUserId());
            } catch (Exception e) {
                log.error("Ошибка синхронизации рейтинга для пользователя {}: {}", 
                        stats.getUserId(), e.getMessage());
            }
        });
        
        log.info("Полная синхронизация рейтингов завершена");
    }
}
