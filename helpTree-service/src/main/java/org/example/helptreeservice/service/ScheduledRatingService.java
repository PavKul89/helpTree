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

    /**
     * Rebuilds stale rating projections.
     *
     * <p>Pagination is cursor-based. A recalculated row stops matching the
     * {@code lastCalculated < threshold} predicate, so an offset would skip the
     * rows that shift into the freed positions. The id cursor continues where
     * the previous batch ended regardless of how the predicate changes.</p>
     */
    @Scheduled(cron = "${rating.calculation.cron:0 0/5 * * * ?}")
    public void recalculateAllRatings() {
        log.info("Начало периодического пересчета рейтингов");

        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        long afterId = 0L;
        int totalProcessed = 0;
        int totalFailed = 0;

        try {
            while (true) {
                List<UserRatingStats> staleStats = statsRepository.findStaleStatsAfter(
                        threshold, afterId, PageRequest.of(0, batchSize));

                if (staleStats.isEmpty()) {
                    break;
                }

                for (UserRatingStats stats : staleStats) {
                    afterId = stats.getId();
                    try {
                        calculationService.calculateUserRating(stats.getUserId());
                        totalProcessed++;

                        if (totalProcessed % 100 == 0) {
                            log.info("Обработано {} пользователей", totalProcessed);
                        }

                    } catch (Exception e) {
                        // Advance the cursor anyway: a permanently failing row
                        // must not block the rest of the batch.
                        totalFailed++;
                        log.error("Ошибка при пересчете рейтинга пользователя ID: {}",
                                stats.getUserId(), e);
                    }
                }

                if (staleStats.size() < batchSize) {
                    break;
                }
            }

            log.info("Завершен периодический пересчет рейтингов. Обработано: {}, ошибок: {}",
                    totalProcessed, totalFailed);

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
