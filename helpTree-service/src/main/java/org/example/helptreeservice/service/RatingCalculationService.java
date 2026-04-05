package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.entity.RatingHistory;
import org.example.helptreeservice.entity.UserRatingStats;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.RatingHistoryRepository;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingCalculationService {

    private final UserRatingStatsRepository statsRepository;
    private final RatingHistoryRepository historyRepository;
    private final HelpRepository helpRepository;

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
        Double newRating = calculateRating(stats);
        
        stats.setCurrentRating(newRating);
        stats.setLastCalculated(LocalDateTime.now());
        
        String trend = determineTrend(oldRating, newRating);
        stats.setRatingTrend(trend);
        
        statsRepository.save(stats);
        
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

    private Double calculateRating(UserRatingStats stats) {
        long successful = stats.getSuccessfulHelps();
        long cancelled = stats.getCancelledHelps();
        long total = successful + cancelled;
        
        if (total == 0) {
            return 3.0;
        }
        
        double successRate = (double) successful / total;
        double baseRating = 1.0 + (successRate * 4.0);
        
        return Math.round(baseRating * 10.0) / 10.0;
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
        long total = stats.getSuccessfulHelps() + stats.getCancelledHelps();
        double successRate = total > 0 ? (double) stats.getSuccessfulHelps() / total * 100 : 0;
        return String.format("{\"successRate\":%.1f,\"totalHelps\":%d}", successRate, stats.getTotalHelpsGiven());
    }
}
