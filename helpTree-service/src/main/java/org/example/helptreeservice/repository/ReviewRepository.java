package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.User;
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
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByHelpAndFromUser(Help help, User fromUser);

    boolean existsByHelpAndFromUser(Help help, User fromUser);

    @Query("SELECT r FROM Review r JOIN FETCH r.fromUser JOIN FETCH r.help WHERE r.help = :help AND (r.deleted = false OR r.deleted IS NULL)")
    List<Review> findByHelpWithDetails(@Param("help") Help help);

    @Query("SELECT r FROM Review r JOIN FETCH r.fromUser WHERE r.toUser = :user AND (r.deleted = false OR r.deleted IS NULL)")
    List<Review> findByToUserWithFromUser(@Param("user") User user);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.toUser = :user AND (r.deleted = false OR r.deleted IS NULL)")
    Double calculateAverageRating(@Param("user") User user);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.toUser.id = :userId "
            + "AND (r.deleted = false OR r.deleted IS NULL)")
    long countActiveByToUserId(@Param("userId") Long userId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.toUser.id = :userId "
            + "AND (r.deleted = false OR r.deleted IS NULL)")
    Double findAverageRatingByToUserId(@Param("userId") Long userId);

    /**
     * Global admin review list. Filters are optional so a single query covers
     * every combination. Soft-deleted reviews are never exposed.
     */
    @Query(value = "SELECT r FROM Review r "
            + "JOIN FETCH r.fromUser JOIN FETCH r.toUser "
            + "JOIN FETCH r.help h JOIN FETCH h.post "
            + "WHERE (r.deleted = false OR r.deleted IS NULL) "
            + "AND (:userId IS NULL OR r.toUser.id = :userId) "
            + "AND (:rating IS NULL OR r.rating = :rating) "
            + "AND (:from IS NULL OR r.createdAt >= :from) "
            + "AND (:to IS NULL OR r.createdAt <= :to) "
            + "ORDER BY r.createdAt DESC, r.id DESC",
            countQuery = "SELECT COUNT(r) FROM Review r "
                    + "WHERE (r.deleted = false OR r.deleted IS NULL) "
                    + "AND (:userId IS NULL OR r.toUser.id = :userId) "
                    + "AND (:rating IS NULL OR r.rating = :rating) "
                    + "AND (:from IS NULL OR r.createdAt >= :from) "
                    + "AND (:to IS NULL OR r.createdAt <= :to)")
    Page<Review> findForAdmin(@Param("userId") Long userId,
                              @Param("rating") Integer rating,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              Pageable pageable);
}
