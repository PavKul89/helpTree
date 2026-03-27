package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    List<Achievement> findByUserIdOrderByEarnedAtDesc(Long userId);
    boolean existsByUserIdAndType(Long userId, org.example.helptreeservice.enums.AchievementType type);
    Optional<Achievement> findFirstByUserIdAndType(Long userId, org.example.helptreeservice.enums.AchievementType type);
}
