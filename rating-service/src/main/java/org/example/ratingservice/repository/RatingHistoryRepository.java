package org.example.ratingservice.repository;

import org.example.ratingservice.entity.RatingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {

    Page<RatingHistory> findByUserIdOrderByCalculatedAtDesc(Long userId, Pageable pageable);

    List<RatingHistory> findByUserIdAndCalculatedAtBetweenOrderByCalculatedAtAsc(
            Long userId, LocalDateTime start, LocalDateTime end);

    Optional<RatingHistory> findFirstByUserIdOrderByCalculatedAtDesc(Long userId);

    @Query("SELECT AVG(rh.newRating) FROM RatingHistory rh WHERE rh.userId = :userId AND rh.calculatedAt >= :since")
    Double getAverageRatingSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(rh) FROM RatingHistory rh WHERE rh.calculatedAt >= :since")
    Long countUpdatesSince(@Param("since") LocalDateTime since);
}
