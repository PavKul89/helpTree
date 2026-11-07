package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.UserBlockRelation;
import org.example.helptreeservice.enums.UserBlockScope;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRelationRepository extends JpaRepository<UserBlockRelation, Long> {

    Optional<UserBlockRelation> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    long deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /**
     * Atomic create-or-update for the ordered pair. The database unique
     * constraint owns the race: concurrent requests either insert once or update
     * the single surviving row, never both. The casts keep a NULL expiry
     * bindable on PostgreSQL native queries.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_blocks_relations (blocker_id, blocked_id, scope, created_at, expires_at)
            VALUES (:blockerId, :blockedId, :scope, CAST(:createdAt AS timestamp), CAST(:expiresAt AS timestamp))
            ON CONFLICT (blocker_id, blocked_id)
            DO UPDATE SET scope = EXCLUDED.scope, expires_at = EXCLUDED.expires_at
            """,
            nativeQuery = true)
    int upsert(@Param("blockerId") Long blockerId,
               @Param("blockedId") Long blockedId,
               @Param("scope") String scope,
               @Param("createdAt") LocalDateTime createdAt,
               @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * Active rows created by {@code blockerId}. Used by the "my blocked users"
     * endpoint, so the blocked profile is fetched eagerly.
     */
    @EntityGraph(attributePaths = {"blocked"})
    @Query("""
            SELECT r FROM UserBlockRelation r
            WHERE r.blocker.id = :blockerId
              AND (r.expiresAt IS NULL OR r.expiresAt > :now)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<UserBlockRelation> findActiveByBlockerId(@Param("blockerId") Long blockerId,
                                                  @Param("now") LocalDateTime now);

    /**
     * Active rows in which the user is either side. Used by block-status and by
     * the PROFILE directory filter.
     */
    @EntityGraph(attributePaths = {"blocker", "blocked"})
    @Query("""
            SELECT r FROM UserBlockRelation r
            WHERE (r.blocker.id = :userId OR r.blocked.id = :userId)
              AND (r.expiresAt IS NULL OR r.expiresAt > :now)
              AND r.scope IN :scopes
            """)
    List<UserBlockRelation> findActiveForParticipant(@Param("userId") Long userId,
                                                     @Param("scopes") Collection<UserBlockScope> scopes,
                                                     @Param("now") LocalDateTime now);

    /**
     * True when an active block covers {@code scopes} between the pair, in either
     * direction. A single lookup backs every enforcement point.
     */
    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END
            FROM UserBlockRelation r
            WHERE ((r.blocker.id = :firstId AND r.blocked.id = :secondId)
                OR (r.blocker.id = :secondId AND r.blocked.id = :firstId))
              AND (r.expiresAt IS NULL OR r.expiresAt > :now)
              AND r.scope IN :scopes
            """)
    boolean existsEffectiveBetween(@Param("firstId") Long firstId,
                                   @Param("secondId") Long secondId,
                                   @Param("scopes") Collection<UserBlockScope> scopes,
                                   @Param("now") LocalDateTime now);
}
