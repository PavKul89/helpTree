package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.UserRatingStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRatingStatsRepository extends JpaRepository<UserRatingStats, Long> {
    
    Optional<UserRatingStats> findByUserId(Long userId);
    
    @Query("SELECT u FROM UserRatingStats u ORDER BY u.currentRating DESC")
    Page<UserRatingStats> findTopRated(Pageable pageable);
    
    @Query("SELECT u FROM UserRatingStats u WHERE u.lastCalculated < :threshold")
    List<UserRatingStats> findStaleStats(LocalDateTime threshold, Pageable pageable);
}
