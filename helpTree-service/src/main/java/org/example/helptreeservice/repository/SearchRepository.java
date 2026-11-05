package org.example.helptreeservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Unified search over the categories that actually exist. The UNION keeps a
 * single ordering/total count across heterogeneous tables; filtering happens in
 * the database, never in memory.
 */
public interface SearchRepository extends Repository<org.example.helptreeservice.entity.User, Long> {

    /**
     * Common projection for every category:
     * type, id, title, snippet, category, city, rating, created_at, owner_id.
     */
    String UNION = """
            SELECT 'POST' AS result_type, p.id AS entity_id, p.title AS title,
                   COALESCE(p.description, '') AS snippet, p.category AS category,
                   u.city AS city, CAST(NULL AS double precision) AS rating,
                   p.created_at AS created_at, p.user_id AS owner_id
            FROM posts p
            JOIN users u ON u.id = p.user_id
            WHERE COALESCE(p.deleted, FALSE) = FALSE
              AND p.status <> 'CANCELLED'
              AND (lower(p.title) LIKE :pattern
                   OR lower(COALESCE(p.description, '')) LIKE :pattern
                   OR lower(COALESCE(p.category, '')) LIKE :pattern
                   OR lower(COALESCE(u.city, '')) LIKE :pattern)
              AND (:cityPattern = '' OR lower(COALESCE(u.city, '')) LIKE :cityPattern)
              AND (:categoryPattern = '' OR lower(COALESCE(p.category, '')) LIKE :categoryPattern)
            UNION ALL
            SELECT 'USER', u.id, u.name, COALESCE(u.city, ''), CAST(NULL AS varchar),
                   u.city, u.rating, u.created_at, u.id
            FROM users u
            WHERE COALESCE(u.deleted, FALSE) = FALSE
              AND (lower(u.name) LIKE :pattern OR lower(COALESCE(u.city, '')) LIKE :pattern)
              AND (:cityPattern = '' OR lower(COALESCE(u.city, '')) LIKE :cityPattern)
              AND :categoryPattern = ''
            UNION ALL
            SELECT 'REVIEW', r.id, tu.name, COALESCE(r.comment, ''), CAST(NULL AS varchar),
                   CAST(NULL AS varchar), r.rating::double precision, r.created_at, r.to_user_id
            FROM reviews r
            JOIN users tu ON tu.id = r.to_user_id
            WHERE COALESCE(r.deleted, FALSE) = FALSE
              AND r.comment IS NOT NULL
              AND btrim(r.comment) <> ''
              AND (lower(r.comment) LIKE :pattern OR lower(tu.name) LIKE :pattern)
              AND :cityPattern = ''
              AND :categoryPattern = ''
            """;

    @Query(value = "SELECT * FROM (" + UNION + ") s "
            + "WHERE (:type = 'ALL' OR s.result_type = :type) "
            + "ORDER BY s.created_at DESC NULLS LAST, s.entity_id DESC",
            countQuery = "SELECT COUNT(*) FROM (" + UNION + ") s "
                    + "WHERE (:type = 'ALL' OR s.result_type = :type)",
            nativeQuery = true)
    Page<Object[]> searchNewest(@Param("pattern") String pattern,
                                @Param("cityPattern") String cityPattern,
                                @Param("categoryPattern") String categoryPattern,
                                @Param("type") String type,
                                Pageable pageable);

    @Query(value = "SELECT * FROM (" + UNION + ") s "
            + "WHERE (:type = 'ALL' OR s.result_type = :type) "
            + "ORDER BY s.created_at ASC NULLS LAST, s.entity_id ASC",
            countQuery = "SELECT COUNT(*) FROM (" + UNION + ") s "
                    + "WHERE (:type = 'ALL' OR s.result_type = :type)",
            nativeQuery = true)
    Page<Object[]> searchOldest(@Param("pattern") String pattern,
                                @Param("cityPattern") String cityPattern,
                                @Param("categoryPattern") String categoryPattern,
                                @Param("type") String type,
                                Pageable pageable);

    /** Small type-ahead helper: at most one page per category. */
    @Query(value = "SELECT * FROM (" + UNION + ") s "
            + "WHERE (:type = 'ALL' OR s.result_type = :type) "
            + "ORDER BY s.created_at DESC NULLS LAST, s.entity_id DESC",
            countQuery = "SELECT COUNT(*) FROM (" + UNION + ") s "
                    + "WHERE (:type = 'ALL' OR s.result_type = :type)",
            nativeQuery = true)
    List<Object[]> searchPreview(@Param("pattern") String pattern,
                                 @Param("cityPattern") String cityPattern,
                                 @Param("categoryPattern") String categoryPattern,
                                 @Param("type") String type,
                                 Pageable pageable);
}
