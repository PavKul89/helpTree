package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.RatingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {
    
    Page<RatingHistory> findByUserIdOrderByCalculatedAtDesc(Long userId, Pageable pageable);

    /**
     * Deletes at most {@code batchSize} expired rows per call. The subquery
     * bounds each transaction, so a large backlog can never hold a long lock or
     * grow the WAL without limit. The caller loops until fewer than
     * {@code batchSize} rows are returned.
     */
    @Modifying
    @Query(value = "DELETE FROM rating_history "
            + "WHERE id IN (SELECT id FROM rating_history "
            + "              WHERE calculated_at < :cutoff "
            + "              ORDER BY calculated_at LIMIT :batchSize)",
            nativeQuery = true)
    int deleteOldBatch(@Param("cutoff") LocalDateTime cutoff,
                       @Param("batchSize") int batchSize);
}
