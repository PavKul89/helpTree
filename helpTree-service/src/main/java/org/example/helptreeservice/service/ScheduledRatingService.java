package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.entity.UserRatingStats;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledRatingService {

    private final UserRatingStatsRepository statsRepository;
    private final RatingCalculationService calculationService;

    @Value("${rating.calculation.batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${rating.calculation.cron:0 0/5 * * * ?}")
    public void recalculateAllRatings() {
        log.info("Начало периодического пересчета рейтингов");

        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        int page = 0;
        int totalProcessed = 0;

        try {
            while (true) {
                List<UserRatingStats> staleStats = statsRepository.findStaleStats(
                        threshold, PageRequest.of(page, batchSize));

                if (staleStats.isEmpty()) {
                    break;
                }

                for (UserRatingStats stats : staleStats) {
                    try {
                        calculationService.calculateUserRating(stats.getUserId());
                        totalProcessed++;

                        if (totalProcessed % 100 == 0) {
                            log.info("Обработано {} пользователей", totalProcessed);
                        }

                    } catch (Exception e) {
                        log.error("Ошибка при пересчете рейтинга пользователя ID: {}",
                                stats.getUserId(), e);
                    }
                }

                page++;
            }

            log.info("Завершен периодический пересчет рейтингов. Всего обработано: {}",
                    totalProcessed);

        } catch (Exception e) {
            log.error("Критическая ошибка при периодическом пересчете рейтингов", e);
        }
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldHistory() {
        log.info("Начало очистки старой истории рейтингов");

        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);

        try {
            log.info("Очистка истории рейтингов до {}", oneYearAgo);

        } catch (Exception e) {
            log.error("Ошибка при очистке истории рейтингов", e);
        }
    }
}
