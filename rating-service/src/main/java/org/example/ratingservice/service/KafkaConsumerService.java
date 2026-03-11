package org.example.ratingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ratingservice.dto.HelpEvent;
import org.example.ratingservice.entity.UserRatingStats;
import org.example.ratingservice.repository.UserRatingStatsRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final UserRatingStatsRepository statsRepository;
    private final RatingCalculationService ratingCalculationService;
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @KafkaListener(topics = "help-events", groupId = "rating-service-group")
    public void consumeHelpEvent(String message) {
        try {
            log.info("🔥 ПОЛУЧЕНО СООБЩЕНИЕ: {}", message);

            // Десериализуем строку в объект HelpEvent
            HelpEvent event = objectMapper.readValue(message, HelpEvent.class);

            log.info("📦 ПРЕОБРАЗОВАНО: type={}, helpId={}, helperId={}, receiverId={}",
                    event.getEventType(), event.getHelpId(), event.getHelperId(), event.getReceiverId());

            switch (event.getEventType()) {
                case "ACCEPTED":
                    log.info("✅ ОБРАБОТКА ACCEPTED");
                    handleAcceptedEvent(event);
                    break;
                case "COMPLETED":
                    log.info("✅ ОБРАБОТКА COMPLETED, duration={}", event.getDuration());
                    handleCompletedEvent(event);
                    break;
                case "CONFIRMED":
                    log.info("✅ ОБРАБОТКА CONFIRMED");
                    handleConfirmedEvent(event);
                    break;
                case "CANCELLED":
                    log.info("✅ ОБРАБОТКА CANCELLED");
                    handleCancelledEvent(event);
                    break;
                default:
                    log.warn("⚠️ НЕИЗВЕСТНЫЙ ТИП: {}", event.getEventType());
            }

        } catch (Exception e) {
            log.error("❌ ОШИБКА ПРИ ОБРАБОТКЕ: {}", e.getMessage(), e);
        }
    }

    private void handleAcceptedEvent(HelpEvent event) {
        updateHelperStats(event.getHelperId(), "accepted");
        updateReceiverStats(event.getReceiverId(), "accepted");
    }

    private void handleCompletedEvent(HelpEvent event) {
        if (event.getDuration() != null) {
            updateResponseTime(event.getHelperId(), event.getDuration());
        }
    }

    private void handleConfirmedEvent(HelpEvent event) {
        updateHelperStats(event.getHelperId(), "confirmed");

        log.info("🔄 Пересчет рейтинга для helperId={}", event.getHelperId());
        ratingCalculationService.calculateUserRating(event.getHelperId());

        log.info("🔄 Пересчет рейтинга для receiverId={}", event.getReceiverId());
        ratingCalculationService.calculateUserRating(event.getReceiverId());
    }

    private void handleCancelledEvent(HelpEvent event) {
        updateHelperStats(event.getHelperId(), "cancelled");
        ratingCalculationService.calculateUserRating(event.getHelperId());
    }

    private void updateHelperStats(Long helperId, String action) {
        UserRatingStats stats = statsRepository.findByUserId(helperId)
                .orElseGet(() -> createNewStats(helperId));

        switch (action) {
            case "accepted":
                stats.setTotalHelpsGiven(stats.getTotalHelpsGiven() + 1);
                log.info("📈 helperId={}: totalHelpsGiven -> {}", helperId, stats.getTotalHelpsGiven());
                break;
            case "confirmed":
                stats.setSuccessfulHelps(stats.getSuccessfulHelps() + 1);
                log.info("📈 helperId={}: successfulHelps -> {}", helperId, stats.getSuccessfulHelps());
                break;
            case "cancelled":
                stats.setCancelledHelps(stats.getCancelledHelps() + 1);
                log.info("📈 helperId={}: cancelledHelps -> {}", helperId, stats.getCancelledHelps());
                break;
        }

        statsRepository.save(stats);
    }

    private void updateReceiverStats(Long receiverId, String action) {
        UserRatingStats stats = statsRepository.findByUserId(receiverId)
                .orElseGet(() -> createNewStats(receiverId));

        if ("accepted".equals(action)) {
            stats.setTotalHelpsReceived(stats.getTotalHelpsReceived() + 1);
            log.info("📈 receiverId={}: totalHelpsReceived -> {}", receiverId, stats.getTotalHelpsReceived());
            statsRepository.save(stats);
        }
    }

    private void updateResponseTime(Long helperId, Long duration) {
        UserRatingStats stats = statsRepository.findByUserId(helperId)
                .orElseGet(() -> createNewStats(helperId));

        if (stats.getAverageResponseTime() == null) {
            stats.setAverageResponseTime(duration.doubleValue());
            log.info("⏱️ helperId={}: среднее время = {} мин", helperId, duration);
        } else {
            double total = stats.getAverageResponseTime() * stats.getTotalHelpsGiven();
            stats.setAverageResponseTime((total + duration) / (stats.getTotalHelpsGiven() + 1));
            log.info("⏱️ helperId={}: среднее время обновлено -> {} мин",
                    helperId, stats.getAverageResponseTime());
        }

        statsRepository.save(stats);
    }

    private UserRatingStats createNewStats(Long userId) {
        log.info("🆕 Создание статистики для userId={}", userId);
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
    }
}