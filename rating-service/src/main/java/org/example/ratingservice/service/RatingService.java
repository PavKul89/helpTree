package org.example.ratingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ratingservice.dto.RatingResponse;
import org.example.ratingservice.dto.UserDto;
import org.example.ratingservice.entity.RatingHistory;
import org.example.ratingservice.entity.UserRatingStats;
import org.example.ratingservice.repository.RatingHistoryRepository;
import org.example.ratingservice.repository.UserRatingStatsRepository;
import org.example.ratingservice.client.UserServiceClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingService {

    private final UserRatingStatsRepository statsRepository;
    private final RatingHistoryRepository historyRepository;
    private final UserServiceClient userServiceClient;
    private final RatingCalculationService calculationService;

    /**
     * Получение текущего рейтинга пользователя
     */
    @Cacheable(value = "userRating", key = "#userId")
    public RatingResponse getUserRating(Long userId) {
        log.info("Получение рейтинга для пользователя ID: {}", userId);

        UserDto user = userServiceClient.getUserById(userId);
        UserRatingStats stats = statsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultStats(userId));

        return buildRatingResponse(user, stats);
    }

    /**
     * Получение топа пользователей по рейтингу
     */
    public Page<RatingResponse> getTopRatedUsers(Pageable pageable) {
        log.info("Получение топа пользователей по рейтингу");

        Page<UserRatingStats> topStats = statsRepository.findTopRated(pageable);

        return topStats.map(stats -> {
            try {
                UserDto user = userServiceClient.getUserById(stats.getUserId());
                return buildRatingResponse(user, stats);
            } catch (Exception e) {
                log.error("Ошибка при получении данных пользователя ID: {}", stats.getUserId(), e);
                return null;
            }
        });
    }

    /**
     * Принудительный пересчет рейтинга пользователя
     */
    @CacheEvict(value = "userRating", key = "#userId")
    public RatingResponse recalculateUserRating(Long userId) {
        log.info("Принудительный пересчет рейтинга для пользователя ID: {}", userId);

        Double newRating = calculationService.calculateUserRating(userId);

        UserDto user = userServiceClient.getUserById(userId);
        UserRatingStats stats = statsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Статистика не найдена"));

        return buildRatingResponse(user, stats);
    }

    /**
     * Получение истории изменений рейтинга
     */
    public Page<RatingHistory> getRatingHistory(Long userId, Pageable pageable) {
        log.info("Получение истории рейтинга для пользователя ID: {}", userId);
        return historyRepository.findByUserIdOrderByCalculatedAtDesc(userId, pageable);
    }

    /**
     * Построение ответа с рейтингом
     */
    private RatingResponse buildRatingResponse(UserDto user, UserRatingStats stats) {
        Map<String, Double> components = new HashMap<>();
        components.put("currentRating", stats.getCurrentRating());
        components.put("successRate", calculateSuccessRate(stats));
        components.put("avgResponseTime", stats.getAverageResponseTime());

        return RatingResponse.builder()
                .userId(user.getId())
                .userName(user.getName())
                .overallRating(stats.getCurrentRating())
                .ratingComponents(components)
                .level(determineRatingLevel(stats.getCurrentRating()))
                .calculatedAt(stats.getLastCalculated())
                .totalHelps(stats.getTotalHelpsGiven())
                .totalReceivedHelps(stats.getTotalHelpsReceived())
                .successRate(calculateSuccessRate(stats))
                .build();
    }

    /**
     * Определение уровня рейтинга
     */
    private String determineRatingLevel(Double rating) {
        if (rating >= 4.5) return "EXCELLENT";
        if (rating >= 3.5) return "GOOD";
        if (rating >= 2.5) return "AVERAGE";
        if (rating >= 1.5) return "POOR";
        return "BAD";
    }

    /**
     * Расчет процента успешности
     */
    private Double calculateSuccessRate(UserRatingStats stats) {
        long total = stats.getSuccessfulHelps() + stats.getCancelledHelps();
        if (total == 0) return 0.0;
        return (double) stats.getSuccessfulHelps() / total * 100;
    }

    /**
     * Создание дефолтной статистики
     */
    private UserRatingStats createDefaultStats(Long userId) {
        return UserRatingStats.builder()
                .userId(userId)
                .totalHelpsGiven(0L)
                .totalHelpsReceived(0L)
                .successfulHelps(0L)
                .cancelledHelps(0L)
                .currentRating(3.0)
                .ratingTrend("STABLE")
                .lastCalculated(LocalDateTime.now())
                .build();
    }
}
