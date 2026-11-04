package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.repository.projection.ConfirmedHelpGraphProjection;
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

    @EntityGraph(attributePaths = {"helper", "receiver"})
    @Query("SELECT h FROM Help h WHERE h.helper = :helper AND (h.deleted = false OR h.deleted IS NULL)")
    Page<Help> findByHelperWithDetails(@Param("helper") User helper, Pageable pageable);

    @EntityGraph(attributePaths = {"helper", "receiver"})
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
     * Typed projection of the graph source of truth: confirmed, non-deleted
     * help acts. One row is one help act, therefore {@code helpId} is the only
     * valid edge identity. A user id can repeat for the same helper and must
     * never be used as the edge id.
     */
    @Query("SELECT h.id AS helpId, h.helper.id AS helperId, h.helper.name AS helperName, " +
           "h.receiver.id AS receiverId, h.receiver.name AS receiverName, " +
           "h.post.id AS postId, h.post.title AS postTitle, h.status AS status, " +
           "h.confirmedAt AS confirmedAt " +
           "FROM Help h WHERE h.status = 'CONFIRMED' " +
           "AND (h.deleted = false OR h.deleted IS NULL) " +
           "ORDER BY h.confirmedAt ASC, h.id ASC")
    List<ConfirmedHelpGraphProjection> findConfirmedHelpGraphProjection();

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
}
