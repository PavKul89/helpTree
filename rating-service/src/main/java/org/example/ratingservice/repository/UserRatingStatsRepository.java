package org.example.ratingservice.repository;

import org.example.ratingservice.entity.UserRatingStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRatingStatsRepository extends JpaRepository<UserRatingStats, Long> {

    Optional<UserRatingStats> findByUserId(Long userId);

    @Query("SELECT urs FROM UserRatingStats urs WHERE urs.lastCalculated < :threshold OR urs.lastCalculated IS NULL")
    List<UserRatingStats> findStaleStats(@Param("threshold") LocalDateTime threshold, Pageable pageable);

    @Modifying
    @Query("UPDATE UserRatingStats urs SET urs.currentRating = :rating, urs.lastCalculated = :now WHERE urs.userId = :userId")
    void updateRating(@Param("userId") Long userId,
                      @Param("rating") Double rating,
                      @Param("now") LocalDateTime now);

    @Query("SELECT AVG(urs.currentRating) FROM UserRatingStats urs")
    Double getAverageRating();

    @Query("SELECT urs FROM UserRatingStats urs ORDER BY urs.currentRating DESC")
    Page<UserRatingStats> findTopRated(Pageable pageable);
}
