package org.example.ratingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ratingservice.client.UserServiceClient;
import org.example.ratingservice.dto.RatingUpdateEvent;
import org.example.ratingservice.dto.UserDto;
import org.example.ratingservice.entity.RatingHistory;
import org.example.ratingservice.entity.UserRatingStats;
import org.example.ratingservice.repository.RatingHistoryRepository;
import org.example.ratingservice.repository.UserRatingStatsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingCalculationService {

    private final UserRatingStatsRepository statsRepository;
    private final RatingHistoryRepository historyRepository;
    private final UserServiceClient userServiceClient;
    private final KafkaTemplate<String, String> kafkaTemplate; // Изменено на String
    private ObjectMapper objectMapper;

    @Value("${rating.calculation.weights.completion-weight:1.5}")
    private double completionWeight;

    @Value("${rating.calculation.weights.confirmation-weight:2.0}")
    private double confirmationWeight;

    @Value("${rating.calculation.weights.speed-weight:0.5}")
    private double speedWeight;

    @Value("${rating.calculation.weights.debt-penalty:-0.5}")
    private double debtPenalty;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Transactional
    public Double calculateUserRating(Long userId) {
        log.info("Начало расчета рейтинга для пользователя ID: {}", userId);

        try {
            // Получаем статистику пользователя
            UserRatingStats stats = getOrCreateStats(userId);

            // Получаем данные пользователя из основного сервиса
            UserDto user = userServiceClient.getUserById(userId);

            // Рассчитываем компоненты рейтинга
            Map<String, Double> components = calculateRatingComponents(stats, user);

            // Общий рейтинг
            Double newRating = calculateOverallRating(components);

            // Сохраняем историю
            saveRatingHistory(userId, stats.getCurrentRating(), newRating, components, null);

            // Обновляем статистику
            updateUserStats(stats, newRating);

            // Отправляем событие об изменении рейтинга
            sendRatingUpdateEvent(userId, stats.getCurrentRating(), newRating);

            // Обновляем рейтинг в основном сервисе
            try {
                userServiceClient.updateUserRating(userId, newRating);
                log.info("✅ Рейтинг обновлен в helpTree-service: userId={}, newRating={}", userId, newRating);
            } catch (Exception e) {
                log.error("❌ Не удалось обновить рейтинг в helpTree-service: {}", e.getMessage());
                // Не бросаем исключение, чтобы не прерывать процесс
            }

            log.info("Рейтинг для пользователя ID {} рассчитан: старый={}, новый={}",
                    userId, stats.getCurrentRating(), newRating);

            return newRating;

        } catch (Exception e) {
            log.error("Ошибка при расчете рейтинга для пользователя ID: {}", userId, e);
            throw new RuntimeException("Ошибка расчета рейтинга", e);
        }
    }

    private Map<String, Double> calculateRatingComponents(UserRatingStats stats, UserDto user) {
        Map<String, Double> components = new HashMap<>();

        double successRate = calculateSuccessRate(stats);
        components.put("successRate", successRate);

        double speedRate = calculateSpeedRate(stats);
        components.put("speedRate", speedRate);

        double debtComponent = calculateDebtComponent(user);
        components.put("debtComponent", debtComponent);

        double baseRating = 3.0;
        components.put("baseRating", baseRating);

        return components;
    }

    private Double calculateOverallRating(Map<String, Double> components) {
        double rating = components.get("baseRating");

        rating += components.get("successRate") * completionWeight;
        rating += components.get("speedRate") * speedWeight;
        rating += components.get("debtComponent") * debtPenalty;

        return Math.max(0, Math.min(5, rating));
    }

    private Double calculateSuccessRate(UserRatingStats stats) {
        long total = stats.getSuccessfulHelps() + stats.getCancelledHelps();
        if (total == 0) return 0.0;
        return (double) stats.getSuccessfulHelps() / total;
    }

    private Double calculateSpeedRate(UserRatingStats stats) {
        if (stats.getAverageResponseTime() == null || stats.getAverageResponseTime() == 0) {
            return 0.5;
        }

        double hours = stats.getAverageResponseTime() / 60.0;
        if (hours <= 1) return 1.0;
        if (hours <= 6) return 0.7;
        if (hours <= 24) return 0.4;
        return 0.1;
    }

    private Double calculateDebtComponent(UserDto user) {
        if (user.getDebtCount() == null || user.getDebtCount() == 0) {
            return 0.0;
        }
        return Math.min(2.0, user.getDebtCount() * 0.5);
    }

    private UserRatingStats getOrCreateStats(Long userId) {
        return statsRepository.findByUserId(userId)
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
    }

    private void saveRatingHistory(Long userId, Double oldRating, Double newRating,
                                   Map<String, Double> components, Long helpId) {
        try {
            String componentsStr = objectMapper.writeValueAsString(components);

            RatingHistory history = RatingHistory.builder()
                    .userId(userId)
                    .oldRating(oldRating)
                    .newRating(newRating)
                    .calculatedAt(LocalDateTime.now())
                    .helpId(helpId)
                    .ratingComponents(componentsStr)
                    .reason("Автоматический расчет рейтинга")
                    .build();

            historyRepository.save(history);
        } catch (Exception e) {
            log.error("Ошибка при сохранении истории рейтинга", e);
        }
    }

    private void updateUserStats(UserRatingStats stats, Double newRating) {
        Double oldRating = stats.getCurrentRating();
        stats.setCurrentRating(newRating);
        stats.setLastCalculated(LocalDateTime.now());

        if (oldRating == null) {
            stats.setRatingTrend("STABLE");
        } else if (newRating > oldRating + 0.1) {
            stats.setRatingTrend("UP");
        } else if (newRating < oldRating - 0.1) {
            stats.setRatingTrend("DOWN");
        } else {
            stats.setRatingTrend("STABLE");
        }

        statsRepository.save(stats);
    }

    private void sendRatingUpdateEvent(Long userId, Double oldRating, Double newRating) {
        try {
            RatingUpdateEvent event = RatingUpdateEvent.builder()
                    .userId(userId)
                    .oldRating(oldRating)
                    .newRating(newRating)
                    .updatedAt(LocalDateTime.now())
                    .reason("Автоматический пересчет")
                    .build();

            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("rating-updates", userId.toString(), message);
            log.info("📤 Отправлено событие обновления рейтинга для пользователя ID: {}", userId);

        } catch (Exception e) {
            log.error("❌ Ошибка при отправке события обновления рейтинга: {}", e.getMessage());
        }
    }
}