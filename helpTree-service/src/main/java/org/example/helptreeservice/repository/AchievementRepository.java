package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Achievement;
import org.example.helptreeservice.enums.AchievementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    List<Achievement> findByUserIdOrderByEarnedAtDesc(Long userId);
    boolean existsByUserIdAndType(Long userId, AchievementType type);
    Optional<Achievement> findFirstByUserIdAndType(Long userId, AchievementType type);

    @Query("SELECT a.type FROM Achievement a WHERE a.user.id = :userId")
    Set<AchievementType> findAchievementTypesByUserId(@Param("userId") Long userId);

    /**
     * Idempotent award backed by the unique (user_id, type) index. Concurrent
     * finalizations of two helps by the same helper cannot award the same
     * achievement twice or fail the whole transaction with a constraint error.
     */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO achievements (user_id, type, earned_at) " +
                   "VALUES (:userId, :type, :earnedAt) " +
                   "ON CONFLICT (user_id, type) DO NOTHING",
           nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("type") String type,
                       @Param("earnedAt") LocalDateTime earnedAt);
}
