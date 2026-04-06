package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.entity.RatingHistory;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserRatingStats;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.RatingHistoryRepository;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingCalculationService {

    private final UserRatingStatsRepository statsRepository;
    private final RatingHistoryRepository historyRepository;
    private final HelpRepository helpRepository;
    private final UserRepository userRepository;

    public Double calculateUserRating(Long userId) {
        log.info("Расчет рейтинга для пользователя ID: {}", userId);

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

        Double oldRating = stats.getCurrentRating();
        Double newRating = calculateRating(stats, userId);
        
        stats.setCurrentRating(newRating);
        stats.setLastCalculated(LocalDateTime.now());
        
        String trend = determineTrend(oldRating, newRating);
        stats.setRatingTrend(trend);
        
        statsRepository.save(stats);
        
        userRepository.findById(userId).ifPresent(user -> {
            user.setRating(newRating);
            userRepository.save(user);
            log.info("Рейтинг пользователя {} в таблице users обновлен на {}", userId, newRating);
        });
        
        RatingHistory history = RatingHistory.builder()
                .userId(userId)
                .oldRating(oldRating)
                .newRating(newRating)
                .calculatedAt(LocalDateTime.now())
                .reason("Автоматический пересчет")
                .ratingComponents(buildComponentsString(stats))
                .build();
        historyRepository.save(history);
        
        log.info("Рейтинг пользователя {} изменен с {} на {}", userId, oldRating, newRating);
        
        return newRating;
    }

    private Double calculateRating(UserRatingStats stats, Long userId) {
        double rating = 3.0;
        
        rating += stats.getSuccessfulHelps() * 0.1;
        rating -= stats.getCancelledHelps() * 0.2;
        rating -= stats.getTotalHelpsReceived() * 0.05;
        
        var userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            var user = userOpt.get();
            if (user.getDebtCount() != null && user.getDebtCount() > 5) {
                rating -= 0.5;
            }
            if (user.getBlockedAt() != null || (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(java.time.LocalDateTime.now()))) {
                rating -= 1.0;
            }
        }
        
        rating = Math.max(rating, 1.0);
        rating = Math.min(rating, 5.0);
        
        return Math.round(rating * 10.0) / 10.0;
    }

    private String determineTrend(Double oldRating, Double newRating) {
        if (newRating > oldRating + 0.1) {
            return "UP";
        } else if (newRating < oldRating - 0.1) {
            return "DOWN";
        }
        return "STABLE";
    }

    private String buildComponentsString(UserRatingStats stats) {
        return String.format("{\"successful\":%d,\"cancelled\":%d,\"received\":%d}", 
            stats.getSuccessfulHelps(), stats.getCancelledHelps(), stats.getTotalHelpsReceived());
    }
}
