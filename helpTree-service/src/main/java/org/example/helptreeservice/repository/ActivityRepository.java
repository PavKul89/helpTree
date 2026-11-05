package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only activity feed built by a single native UNION.
 *
 * <p>Every source contributes its own visibility predicate, so a public viewer
 * never sees an unconfirmed help, a cancelled post or a moderation event. The
 * owner (and an admin) sees the full history. Pagination, filtering and
 * ordering happen in PostgreSQL; the service never loads whole tables into
 * memory.</p>
 *
 * <p>Projection columns:
 * {@code event_type, event_id, event_time, related_user_id, related_user_name,
 * related_post_id, related_post_title, category, status, detail}.</p>
 */
public interface ActivityRepository extends Repository<User, Long> {

    String UNION = """
            SELECT 'HELP_GIVEN' AS event_type,
                   h.id AS event_id,
                   COALESCE(h.confirmed_at, h.completed_at, h.accepted_at, h.created_at) AS event_time,
                   h.receiver_id AS related_user_id,
                   ru.name AS related_user_name,
                   h.post_id AS related_post_id,
                   p.title AS related_post_title,
                   p.category AS category,
                   CAST(h.status AS text) AS status,
                   p.title AS detail
            FROM helps h
            JOIN posts p ON p.id = h.post_id
            JOIN users ru ON ru.id = h.receiver_id
            WHERE h.helper_id = :userId
              AND COALESCE(h.deleted, FALSE) = FALSE
              AND (:includePrivate = TRUE OR h.status = 'CONFIRMED')
            UNION ALL
            SELECT 'HELP_RECEIVED',
                   h.id,
                   COALESCE(h.confirmed_at, h.completed_at, h.accepted_at, h.created_at),
                   h.helper_id,
                   hu.name,
                   h.post_id,
                   p.title,
                   p.category,
                   CAST(h.status AS text),
                   p.title
            FROM helps h
            JOIN posts p ON p.id = h.post_id
            JOIN users hu ON hu.id = h.helper_id
            WHERE h.receiver_id = :userId
              AND COALESCE(h.deleted, FALSE) = FALSE
              AND (:includePrivate = TRUE OR h.status = 'CONFIRMED')
            UNION ALL
            SELECT 'POST_CREATED',
                   p.id,
                   COALESCE(p.created_at, p.updated_at, CURRENT_TIMESTAMP),
                   CAST(NULL AS bigint),
                   CAST(NULL AS varchar),
                   p.id,
                   p.title,
                   p.category,
                   CAST(p.status AS text),
                   p.title
            FROM posts p
            WHERE p.user_id = :userId
              AND COALESCE(p.deleted, FALSE) = FALSE
              AND (:includePrivate = TRUE
                   OR p.status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED'))
            UNION ALL
            SELECT 'ACHIEVEMENT_EARNED',
                   a.id,
                   a.earned_at,
                   CAST(NULL AS bigint),
                   CAST(NULL AS varchar),
                   CAST(NULL AS bigint),
                   CAST(NULL AS varchar),
                   CAST(NULL AS varchar),
                   CAST(NULL AS varchar),
                   CAST(a.type AS text)
            FROM achievements a
            WHERE a.user_id = :userId
              AND a.earned_at IS NOT NULL
            UNION ALL
            SELECT 'USER_BLOCKED',
                   u.id,
                   u.blocked_at,
                   CAST(NULL AS bigint),
                   CAST(NULL AS varchar),
                   CAST(NULL AS bigint),
                   CAST(NULL AS varchar),
                   CAST(NULL AS varchar),
                   CAST(NULL AS varchar),
                   CAST(NULL AS varchar)
            FROM users u
            WHERE u.id = :userId
              AND :includePrivate = TRUE
              AND u.blocked_at IS NOT NULL
            """;

    @Query(value = "SELECT * FROM (" + UNION + ") a "
            + "WHERE (:eventType = 'ALL' OR a.event_type = :eventType) "
            + "ORDER BY a.event_time DESC, a.event_id DESC",
            countQuery = "SELECT COUNT(*) FROM (" + UNION + ") a "
                    + "WHERE (:eventType = 'ALL' OR a.event_type = :eventType)",
            nativeQuery = true)
    Page<Object[]> findUserActivity(@Param("userId") Long userId,
                                    @Param("includePrivate") boolean includePrivate,
                                    @Param("eventType") String eventType,
                                    Pageable pageable);
}
