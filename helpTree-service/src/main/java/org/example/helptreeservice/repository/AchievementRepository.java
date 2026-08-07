package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Achievement;
import org.example.helptreeservice.enums.AchievementType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    List<Achievement> findByUserIdOrderByEarnedAtDesc(Long userId);
    boolean existsByUserIdAndType(Long userId, AchievementType type);
    Optional<Achievement> findFirstByUserIdAndType(Long userId, AchievementType type);

    @org.springframework.data.jpa.repository.Query("SELECT a.type FROM Achievement a WHERE a.user.id = :userId")
    Set<AchievementType> findAchievementTypesByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
