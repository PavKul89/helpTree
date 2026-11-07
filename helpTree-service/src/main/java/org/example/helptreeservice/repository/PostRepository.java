package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    
    /**
     * Public author feed. Hidden/removed posts stay in the author's own view
     * only, so a plain author list never exposes them.
     */
    @EntityGraph(attributePaths = {"user", "helper"})
    @Query("SELECT p FROM Post p WHERE p.user.id = :userId "
            + "AND (p.deleted = false OR p.deleted IS NULL) "
            + "AND p.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
    Page<Post> findByUserId(@Param("userId") Long userId, Pageable pageable);
    
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.id = :id")
    Post findByIdWithUser(@Param("id") Long id);

    /**
     * Public lookup: a moderated post is treated as absent. Administrative and
     * owner flows use {@link #findByIdForUpdate(Long)} or
     * {@link #findByIdWithUser(Long)}, which deliberately ignore visibility.
     */
    @Query("SELECT p FROM Post p WHERE p.id = :id "
            + "AND (p.deleted = false OR p.deleted IS NULL) "
            + "AND p.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
    Optional<Post> findByIdAndDeletedFalse(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COUNT(p) > 0 FROM Post p WHERE p.id = :id "
            + "AND (p.deleted = false OR p.deleted IS NULL) "
            + "AND p.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
    boolean existsByIdAndDeletedFalse(@Param("id") Long id);
    
    long countByUserId(Long userId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.id IN :ids "
            + "AND (p.deleted = false OR p.deleted IS NULL) "
            + "AND p.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
    List<Post> findByIdsWithUser(@Param("ids") List<Long> ids);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.status = :status "
            + "AND (p.deleted = false OR p.deleted IS NULL) "
            + "AND p.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
    long countByStatus(@Param("status") PostStatus status);

    /**
     * Atomically charges nothing but extends the boost window. The caller has
     * already debited the payer in the same transaction; a zero update count
     * means the post became unavailable and the whole transaction must roll back.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE posts
            SET boosted_until = CASE
                    WHEN boosted_until IS NOT NULL AND boosted_until > :now
                        THEN boosted_until + make_interval(hours => :hours)
                    ELSE :newBoostUntil
                END,
                updated_at = :now
            WHERE id = :postId
              AND COALESCE(deleted, FALSE) = FALSE
              AND moderation_status = 'ACTIVE'
              AND status = 'OPEN'
              AND (:privileged = TRUE OR user_id = :payerId)
            """, nativeQuery = true)
    int applyBoost(
            @Param("postId") Long postId,
            @Param("payerId") Long payerId,
            @Param("privileged") boolean privileged,
            @Param("now") LocalDateTime now,
            @Param("newBoostUntil") LocalDateTime newBoostUntil,
            @Param("hours") int hours);

    @Modifying
    @Query("UPDATE Post p SET p.boostedUntil = NULL WHERE p.boostedUntil IS NOT NULL AND p.boostedUntil <= :now")
    int clearExpiredBoosts(@Param("now") LocalDateTime now);

    @Query(value = "SELECT * FROM posts p WHERE p.deleted = false AND p.moderation_status = 'ACTIVE' ORDER BY CASE WHEN p.boosted_until > CURRENT_TIMESTAMP THEN 1 ELSE 0 END DESC, p.created_at DESC",
           countQuery = "SELECT COUNT(*) FROM posts p WHERE p.deleted = false AND p.moderation_status = 'ACTIVE'",
           nativeQuery = true)
    Page<Post> findAllNotDeletedPaginated(Pageable pageable);

    /**
     * Atomic counter updates avoid lost increments when two comments are
     * created concurrently. The comment row itself is flushed first.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Post p SET p.commentsCount = COALESCE(p.commentsCount, 0) + 1 " +
           "WHERE p.id = :postId AND (p.deleted = false OR p.deleted IS NULL) " +
           "AND p.moderationStatus = org.example.helptreeservice.enums.ModerationStatus.ACTIVE")
    int incrementCommentsCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Post p SET p.commentsCount = " +
           "CASE WHEN COALESCE(p.commentsCount, 0) > 0 THEN p.commentsCount - 1 ELSE 0 END " +
           "WHERE p.id = :postId")
    int decrementCommentsCount(@Param("postId") Long postId);

    @Query("SELECT p.commentsCount FROM Post p WHERE p.id = :postId")
    Integer findCommentsCountById(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM post_images WHERE image_url = :url", nativeQuery = true)
    int deletePostImageUrl(@Param("url") String url);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM post_images WHERE image_url = :url)",
           nativeQuery = true)
    boolean existsPostImageUrl(@Param("url") String url);
}
