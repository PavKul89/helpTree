package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * Serialises reply/edit changes for one review. Both operations check a
     * time window, so the row must not change between the check and the save.
     */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"fromUser", "toUser", "help", "help.post"})
    @Query("SELECT r FROM Review r WHERE r.help = :help "
            + "AND (r.deleted = false OR r.deleted IS NULL) "
            + "AND r.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
    Page<Review> findByHelpWithDetails(@Param("help") Help help, Pageable pageable);

    @EntityGraph(attributePaths = {"fromUser", "toUser", "help", "help.post"})
    @Query("SELECT r FROM Review r WHERE r.toUser = :user "
            + "AND (r.deleted = false OR r.deleted IS NULL) "
            + "AND r.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
    Page<Review> findByToUserWithFromUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.toUser = :user "
            + "AND (r.deleted = false OR r.deleted IS NULL) "
            + "AND r.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
    Double calculateAverageRating(@Param("user") User user);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.toUser.id = :userId "
            + "AND (r.deleted = false OR r.deleted IS NULL) "
            + "AND r.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
    long countActiveByToUserId(@Param("userId") Long userId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.toUser.id = :userId "
            + "AND (r.deleted = false OR r.deleted IS NULL) "
            + "AND r.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
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

    /** Every review written by or received by the user, for the account export. */
    @EntityGraph(attributePaths = {"fromUser", "toUser", "help", "help.post"})
    @Query("SELECT r FROM Review r WHERE r.fromUser.id = :userId OR r.toUser.id = :userId "
            + "ORDER BY r.createdAt DESC, r.id DESC")
    List<Review> findAllForExport(@Param("userId") Long userId);
}
