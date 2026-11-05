package org.example.helptreeservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.repository.RatingHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Deletes expired rating history in bounded batches.
 *
 * <p>Each call is its own transaction, so the scheduler never deletes an
 * unbounded number of rows in one go. The caller owns the loop and can stop
 * between batches, which keeps lock time, WAL growth and replication lag
 * proportional to {@code batchSize} rather than to the whole backlog.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RatingHistoryCleanupService {

    private final RatingHistoryRepository historyRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public int deleteBatch(LocalDateTime cutoff, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        int deleted = historyRepository.deleteOldBatch(cutoff, batchSize);
        if (deleted > 0) {
            Counter.builder("helptree.rating.history.deleted")
                    .description("Rating history rows removed by the retention job")
                    .register(meterRegistry)
                    .increment(deleted);
            log.info("Удалено {} записей истории рейтингов старше {}", deleted, cutoff);
        }
        return deleted;
    }
}
