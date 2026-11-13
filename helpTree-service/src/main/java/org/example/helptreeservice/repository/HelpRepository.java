package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface HelpRepository extends JpaRepository<Help, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Help h WHERE h.id = :helpId")
    Optional<Help> findByIdForUpdate(@Param("helpId") Long helpId);

    @Query("SELECT h FROM Help h JOIN FETCH h.post JOIN FETCH h.helper WHERE h.post = :post AND (h.deleted = false OR h.deleted IS NULL)")
    List<Help> findByPostWithHelper(@Param("post") Post post);

    @Query("SELECT h FROM Help h JOIN FETCH h.post JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.post = :post AND (h.deleted = false OR h.deleted IS NULL)")
    List<Help> findByPost(@Param("post") Post post);

    @Query("SELECT h FROM Help h JOIN FETCH h.post JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.post.id = :postId AND (h.deleted = false OR h.deleted IS NULL)")
    List<Help> findByPostId(@Param("postId") Long postId);

    @EntityGraph(attributePaths = {"post", "helper", "receiver"})
    @Query("SELECT h FROM Help h WHERE h.post.id = :postId AND (h.deleted = false OR h.deleted IS NULL)")
    Page<Help> findByPostId(@Param("postId") Long postId, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "helper", "receiver"})
    @Query("SELECT h FROM Help h WHERE h.helper = :helper AND (h.deleted = false OR h.deleted IS NULL)")
    Page<Help> findByHelperWithDetails(@Param("helper") User helper, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "helper", "receiver"})
    @Query("SELECT h FROM Help h WHERE h.receiver = :receiver AND (h.deleted = false OR h.deleted IS NULL)")
    Page<Help> findByReceiverWithDetails(@Param("receiver") User receiver, Pageable pageable);

    Optional<Help> findByPostAndStatusNot(Post post, HelpStatus status);

    boolean existsByPostAndHelper(Post post, User helper);

    @Query("SELECT COUNT(h) > 0 FROM Help h " +
           "WHERE h.post.id = :postId AND h.status IN :statuses " +
           "AND (h.deleted = false OR h.deleted IS NULL)")
    boolean existsByPostIdAndStatusIn(@Param("postId") Long postId,
                                      @Param("statuses") Collection<HelpStatus> statuses);

    @Query("SELECT h.id FROM Help h " +
           "WHERE h.status = :status AND h.acceptedAt IS NOT NULL AND h.acceptedAt < :cutoff " +
           "AND (h.deleted = false OR h.deleted IS NULL) " +
           "ORDER BY h.acceptedAt ASC")
    List<Long> findIdsByStatusAndAcceptedAtBefore(@Param("status") HelpStatus status,
                                                  @Param("cutoff") LocalDateTime cutoff,
                                                  Pageable pageable);

    @Query("SELECT h.id FROM Help h " +
           "WHERE h.status = :status AND h.completedAt IS NOT NULL AND h.completedAt < :cutoff " +
           "AND (h.deleted = false OR h.deleted IS NULL) " +
           "ORDER BY h.completedAt ASC")
    List<Long> findIdsByStatusAndCompletedAtBefore(@Param("status") HelpStatus status,
                                                   @Param("cutoff") LocalDateTime cutoff,
                                                   Pageable pageable);

    /**
     * Helps that crossed at least one reminder threshold and have not had that
     * reminder delivered yet. The 80% branch is checked independently so a help
     * that appears after both thresholds still gets a single reminder.
     */
    @Query("SELECT h.id FROM Help h " +
           "WHERE h.status = :status AND h.acceptedAt IS NOT NULL " +
           "AND (h.deleted = false OR h.deleted IS NULL) " +
           "AND ((h.reminder50SentAt IS NULL AND h.acceptedAt <= :cutoffFirst) " +
           "     OR (h.reminder80SentAt IS NULL AND h.acceptedAt <= :cutoffSecond)) " +
           "ORDER BY h.acceptedAt ASC")
    List<Long> findIdsForSlaReminder(@Param("status") HelpStatus status,
                                     @Param("cutoffFirst") LocalDateTime cutoffFirst,
                                     @Param("cutoffSecond") LocalDateTime cutoffSecond,
                                     Pageable pageable);

    @Query("SELECT h FROM Help h JOIN FETCH h.post JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.status = :status AND (h.deleted = false OR h.deleted IS NULL)")
    List<Help> findByStatusAndDeletedFalse(@Param("status") HelpStatus status);

    @Query("SELECT h FROM Help h JOIN FETCH h.post JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.helper = :helper AND h.status = :status AND (h.deleted = false OR h.deleted IS NULL)")
    List<Help> findByHelperAndStatus(@Param("helper") User helper, @Param("status") HelpStatus status);

    @Query("SELECT h FROM Help h LEFT JOIN FETCH h.post LEFT JOIN FETCH h.helper LEFT JOIN FETCH h.receiver WHERE h.deleted = false OR h.deleted IS NULL")
    List<Help> findAllWithDetails();

    @Query("SELECT COUNT(h) FROM Help h WHERE h.deleted = false OR h.deleted IS NULL")
    long countActive();

    @Query("SELECT COUNT(h) FROM Help h WHERE h.status = :status "
            + "AND (h.deleted = false OR h.deleted IS NULL)")
    long countByStatus(@Param("status") HelpStatus status);

    @Query("SELECT COUNT(h) FROM Help h WHERE h.status IN :statuses "
            + "AND (h.deleted = false OR h.deleted IS NULL)")
    long countByStatusIn(@Param("statuses") Collection<HelpStatus> statuses);

    @Query("SELECT COUNT(h) FROM Help h WHERE h.helper.id = :userId "
            + "AND (h.deleted = false OR h.deleted IS NULL)")
    long countAllByHelperId(@Param("userId") Long userId);

    @Query("SELECT COUNT(h) FROM Help h WHERE h.helper.id = :userId AND h.status = :status "
            + "AND (h.deleted = false OR h.deleted IS NULL)")
    long countByHelperIdAndStatus(@Param("userId") Long userId,
                                  @Param("status") HelpStatus status);

    @Query("SELECT COUNT(h) FROM Help h WHERE h.helper.id = :userId AND h.status IN :statuses "
            + "AND (h.deleted = false OR h.deleted IS NULL)")
    long countByHelperIdAndStatusIn(@Param("userId") Long userId,
                                    @Param("statuses") Collection<HelpStatus> statuses);

    @Query("SELECT COUNT(h) FROM Help h WHERE h.receiver.id = :userId AND h.status = :status "
            + "AND (h.deleted = false OR h.deleted IS NULL)")
    long countByReceiverIdAndStatus(@Param("userId") Long userId,
                                    @Param("status") HelpStatus status);

    @Query("SELECT FUNCTION('TO_CHAR', h.confirmedAt, 'MM/YYYY') AS monthKey, COUNT(h) " +
           "FROM Help h WHERE h.status = 'CONFIRMED' AND h.confirmedAt IS NOT NULL " +
           "AND (h.deleted = false OR h.deleted IS NULL) " +
           "GROUP BY FUNCTION('TO_CHAR', h.confirmedAt, 'MM/YYYY')")
    List<Object[]> countConfirmedByMonth();

    @Query("SELECT p.category, COUNT(h) " +
           "FROM Help h JOIN h.post p " +
           "WHERE h.status = 'CONFIRMED' AND (h.deleted = false OR h.deleted IS NULL) " +
           "GROUP BY p.category")
    List<Object[]> countConfirmedByCategory();

    @Query("SELECT h.helper.id, COUNT(h) " +
           "FROM Help h WHERE h.status = 'CONFIRMED' AND (h.deleted = false OR h.deleted IS NULL) " +
           "GROUP BY h.helper.id ORDER BY COUNT(h) DESC")
    List<Object[]> countHelpsByHelper();

    /**
     * One row is one confirmed help act. The edge identity is the help id, never
     * a user id, because the same helper can have many separate acts.
     *
     * <p>The recursive CTE runs in the database and is bounded by {@code depth}
     * and a hard {@code LIMIT}. Cycle detection is delegated to PostgreSQL's
     * {@code CYCLE} clause, so a cyclic graph cannot make the query run away.</p>
     */
    String REACHABLE_CTE = """
            WITH RECURSIVE reachable(id, depth) AS (
                SELECT CAST(:userId AS bigint), 0
                UNION ALL
                SELECT h.receiver_id, r.depth + 1
                FROM helps h
                JOIN reachable r ON h.helper_id = r.id
                WHERE h.status = 'CONFIRMED'
                  AND COALESCE(h.deleted, FALSE) = FALSE
                  AND (CAST(:depth AS integer) IS NULL OR r.depth < CAST(:depth AS integer))
            )
            CYCLE id SET is_cycle USING path
            """;

    String GRAPH_EDGE_SELECT = """
            SELECT h.id AS help_id,
                   h.helper_id AS helper_id,
                   hu.name AS helper_name,
                   h.receiver_id AS receiver_id,
                   ru.name AS receiver_name,
                   h.post_id AS post_id,
                   p.title AS post_title,
                   CAST(h.status AS text) AS status,
                   h.confirmed_at AS confirmed_at
            FROM helps h
            JOIN posts p ON p.id = h.post_id
            JOIN users hu ON hu.id = h.helper_id
            JOIN users ru ON ru.id = h.receiver_id
            """;

    String GRAPH_USER_FILTER = """
            WHERE h.status = 'CONFIRMED'
              AND COALESCE(h.deleted, FALSE) = FALSE
              AND h.helper_id IN (SELECT id FROM reachable)
              AND h.receiver_id IN (SELECT id FROM reachable)
            """;

    @Query(value = REACHABLE_CTE + GRAPH_EDGE_SELECT + GRAPH_USER_FILTER
            + " ORDER BY h.confirmed_at ASC, h.id ASC LIMIT CAST(:limit AS integer)",
            nativeQuery = true)
    List<Object[]> findUserGraphEdges(@Param("userId") Long userId,
                                      @Param("depth") Integer depth,
                                      @Param("limit") int limit);

    @Query(value = REACHABLE_CTE + "SELECT COUNT(*) FROM helps h " + GRAPH_USER_FILTER,
            nativeQuery = true)
    long countUserGraphEdges(@Param("userId") Long userId,
                             @Param("depth") Integer depth);

    @Query(value = GRAPH_EDGE_SELECT
            + " WHERE h.status = 'CONFIRMED' AND COALESCE(h.deleted, FALSE) = FALSE"
            + " ORDER BY h.confirmed_at ASC, h.id ASC LIMIT CAST(:limit AS integer)",
            nativeQuery = true)
    List<Object[]> findGlobalGraphEdges(@Param("limit") int limit);

    @Query(value = "SELECT COUNT(*) FROM helps h "
            + "WHERE h.status = 'CONFIRMED' AND COALESCE(h.deleted, FALSE) = FALSE",
            nativeQuery = true)
    long countGlobalGraphEdges();

    @Query("SELECT COUNT(h) FROM Help h " +
           "WHERE h.post.user.id = :userId AND h.createdAt > :since " +
           "AND (h.deleted = false OR h.deleted IS NULL)")
    long countNewResponsesSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT h.id, h.post.id, h.post.title, h.helper.name, h.createdAt " +
           "FROM Help h " +
           "WHERE h.post.user.id = :userId " +
           "AND (h.deleted = false OR h.deleted IS NULL) " +
           "ORDER BY h.createdAt DESC")
    List<Object[]> findNewResponsesData(@Param("userId") Long userId);

    /** Every help act where the user was helper or receiver, for the export. */
    @EntityGraph(attributePaths = {"post", "helper", "receiver"})
    @Query("SELECT h FROM Help h WHERE h.helper.id = :userId OR h.receiver.id = :userId "
            + "ORDER BY h.createdAt DESC, h.id DESC")
    List<Help> findAllForExport(@Param("userId") Long userId);
}
