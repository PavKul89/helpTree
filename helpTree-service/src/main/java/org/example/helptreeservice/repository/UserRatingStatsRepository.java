package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.UserRatingStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRatingStatsRepository extends JpaRepository<UserRatingStats, Long> {
    
    Optional<UserRatingStats> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserRatingStats s WHERE s.userId = :userId")
    Optional<UserRatingStats> findByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * Creates the stats row atomically. A plain save-then-insert would let two
     * concurrent help finalizations for the same user race on the unique
     * user_id constraint and fail one of the help transactions.
     */
    @Modifying
    @Query(value = "INSERT INTO user_rating_stats "
            + "(user_id, total_helps_given, total_helps_received, successful_helps, "
            + "cancelled_helps, current_rating, rating_trend, last_calculated, updated_at) "
            + "VALUES (:userId, 0, 0, 0, 0, 3.0, 'STABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId);
    
    @Query("SELECT s FROM UserRatingStats s JOIN User u ON u.id = s.userId " +
            "WHERE u.deleted = false ORDER BY s.currentRating DESC")
    Page<UserRatingStats> findTopRated(Pageable pageable);
    
    /**
     * {@code lastCalculated IS NULL} is a deliberate "recalculate me" marker
     * written by V43 and by the projection code when facts change.
     *
     * <p>Pagination uses an id cursor instead of an offset: recalculating a row
     * moves it out of the result set, so an advancing offset would silently
     * skip rows. The cursor also guarantees forward progress even when a single
     * row keeps failing.</p>
     */
    @Query("SELECT u FROM UserRatingStats u "
            + "WHERE (u.lastCalculated IS NULL OR u.lastCalculated < :threshold) "
            + "AND u.id > :afterId ORDER BY u.id ASC")
    List<UserRatingStats> findStaleStatsAfter(@Param("threshold") LocalDateTime threshold,
                                              @Param("afterId") Long afterId,
                                              Pageable pageable);
}
