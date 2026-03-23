package org.example.ratingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ratingservice.client.UserServiceClient;
import org.example.ratingservice.entity.UserRatingStats;
import org.example.ratingservice.repository.UserRatingStatsRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingSyncService {

    private final UserRatingStatsRepository statsRepository;
    private final RatingCalculationService calculationService;
    private final UserServiceClient userServiceClient;

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
                            .build();
                    return statsRepository.save(newStats);
                });

        calculationService.calculateUserRating(userId);
        log.info("Синхронизация завершена для пользователя ID: {}", userId);
    }

    public void syncAllRatings() {
        log.info("Синхронизация всех рейтингов");
        
        List<UserRatingStats> allStats = statsRepository.findAll();
        for (UserRatingStats stats : allStats) {
            try {
                calculationService.calculateUserRating(stats.getUserId());
                log.info("Синхронизирован пользователь ID: {}", stats.getUserId());
            } catch (Exception e) {
                log.error("Ошибка синхронизации для пользователя ID: {}", stats.getUserId(), e);
            }
        }
        
        log.info("Синхронизация всех рейтингов завершена");
    }
}
