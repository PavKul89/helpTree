package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
    
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.user.id = :userId AND (p.deleted = false OR p.deleted IS NULL)")
    List<Post> findByUserId(@Param("userId") Long userId);
    
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.id = :id")
    Post findByIdWithUser(@Param("id") Long id);

    Optional<Post> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") Long id);

    boolean existsByIdAndDeletedFalse(Long id);
    
    long countByUserId(Long userId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.deleted = false AND p.status = 'OPEN'")
    List<Post> findAllActive();

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.id IN :ids AND (p.deleted = false OR p.deleted IS NULL)")
    List<Post> findByIdsWithUser(@Param("ids") List<Long> ids);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.deleted = false OR p.deleted IS NULL")
    List<Post> findAllNotDeleted();

    @Query("SELECT COUNT(p) FROM Post p WHERE p.status = :status AND (p.deleted = false OR p.deleted IS NULL)")
    long countByStatus(@Param("status") PostStatus status);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.helper WHERE p.deleted = false ORDER BY CASE WHEN p.boostedUntil > CURRENT_TIMESTAMP THEN 1 ELSE 0 END DESC, p.createdAt DESC")
    List<Post> findAllNotDeletedOrderByBoostedFirst();

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

    @Query(value = "SELECT * FROM posts p WHERE p.deleted = false ORDER BY CASE WHEN p.boosted_until > CURRENT_TIMESTAMP THEN 1 ELSE 0 END DESC, p.created_at DESC",
           countQuery = "SELECT COUNT(*) FROM posts p WHERE p.deleted = false",
           nativeQuery = true)
    Page<Post> findAllNotDeletedPaginated(Pageable pageable);
}
