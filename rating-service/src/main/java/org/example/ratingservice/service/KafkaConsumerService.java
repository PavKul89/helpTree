package org.example.ratingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ratingservice.dto.HelpEvent;
import org.example.ratingservice.entity.UserRatingStats;
import org.example.ratingservice.repository.UserRatingStatsRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final UserRatingStatsRepository statsRepository;
    private final RatingCalculationService ratingCalculationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "help-events", groupId = "rating-service-group")
    public void consumeHelpEvent(String message) {
        try {
            HelpEvent event = objectMapper.readValue(message, HelpEvent.class);
            log.info("Получено событие помощи: type={}, helpId={}, helperId={}, receiverId={}",
                    event.getEventType(), event.getHelpId(), event.getHelperId(), event.getReceiverId());

            switch (event.getEventType()) {
                case "ACCEPTED":
                    handleAcceptedEvent(event);
                    break;
                case "COMPLETED":
                    handleCompletedEvent(event);
                    break;
                case "CONFIRMED":
                    handleConfirmedEvent(event);
                    break;
                case "CANCELLED":
                    handleCancelledEvent(event);
                    break;
                default:
                    log.warn("Неизвестный тип события: {}", event.getEventType());
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке события помощи", e);
        }
    }

    private void handleAcceptedEvent(HelpEvent event) {
        // Обновляем статистику помощника
        updateHelperStats(event.getHelperId(), "accepted");

        // Обновляем статистику получателя
        updateReceiverStats(event.getReceiverId(), "accepted");
    }

    private void handleCompletedEvent(HelpEvent event) {
        // Обновляем время реакции помощника
        updateResponseTime(event.getHelperId(), event.getDuration());
    }

    private void handleConfirmedEvent(HelpEvent event) {
        // Увеличиваем счетчик успешных завершений
        updateHelperStats(event.getHelperId(), "confirmed");

        // Пересчитываем рейтинг для обоих пользователей
        ratingCalculationService.calculateUserRating(event.getHelperId());
        ratingCalculationService.calculateUserRating(event.getReceiverId());
    }

    private void handleCancelledEvent(HelpEvent event) {
        // Увеличиваем счетчик отмен
        updateHelperStats(event.getHelperId(), "cancelled");

        // Пересчитываем рейтинг помощника
        ratingCalculationService.calculateUserRating(event.getHelperId());
    }

    private void updateHelperStats(Long helperId, String action) {
        UserRatingStats stats = statsRepository.findByUserId(helperId)
                .orElseGet(() -> createNewStats(helperId));

        switch (action) {
            case "accepted":
                stats.setTotalHelpsGiven(stats.getTotalHelpsGiven() + 1);
                break;
            case "confirmed":
                stats.setSuccessfulHelps(stats.getSuccessfulHelps() + 1);
                break;
            case "cancelled":
                stats.setCancelledHelps(stats.getCancelledHelps() + 1);
                break;
        }

        statsRepository.save(stats);
    }

    private void updateReceiverStats(Long receiverId, String action) {
        UserRatingStats stats = statsRepository.findByUserId(receiverId)
                .orElseGet(() -> createNewStats(receiverId));

        if ("accepted".equals(action)) {
            stats.setTotalHelpsReceived(stats.getTotalHelpsReceived() + 1);
            statsRepository.save(stats);
        }
    }

    private void updateResponseTime(Long helperId, Long duration) {
        if (duration == null) return;

        UserRatingStats stats = statsRepository.findByUserId(helperId)
                .orElseGet(() -> createNewStats(helperId));

        if (stats.getAverageResponseTime() == null) {
            stats.setAverageResponseTime(duration.doubleValue());
        } else {
            // Обновляем среднее время
            double total = stats.getAverageResponseTime() * stats.getTotalHelpsGiven();
            stats.setAverageResponseTime((total + duration) / (stats.getTotalHelpsGiven() + 1));
        }

        statsRepository.save(stats);
    }

    private UserRatingStats createNewStats(Long userId) {
        return UserRatingStats.builder()
                .userId(userId)
                .totalHelpsGiven(0L)
                .totalHelpsReceived(0L)
                .successfulHelps(0L)
                .cancelledHelps(0L)
                .currentRating(3.0)
                .ratingTrend("STABLE")
                .build();
    }
}
