package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HelpRepository extends JpaRepository<Help, Long> {

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

    @Query("SELECT h FROM Help h JOIN FETCH h.post JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.status = :status AND (h.deleted = false OR h.deleted IS NULL)")
    List<Help> findByStatusAndDeletedFalse(@Param("status") HelpStatus status);

    @Query("SELECT h FROM Help h JOIN FETCH h.post JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.helper = :helper AND h.status = :status AND (h.deleted = false OR h.deleted IS NULL)")
    List<Help> findByHelperAndStatus(@Param("helper") User helper, @Param("status") HelpStatus status);

    @Query("SELECT h FROM Help h LEFT JOIN FETCH h.post LEFT JOIN FETCH h.helper LEFT JOIN FETCH h.receiver WHERE h.deleted = false OR h.deleted IS NULL")
    List<Help> findAllWithDetails();

    @Query("SELECT COUNT(h) FROM Help h WHERE h.deleted = false OR h.deleted IS NULL")
    long countActive();

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

    @Query("SELECT h.helper.id, h.helper.name, h.receiver.id, h.receiver.name, " +
           "h.post.id, h.post.title, h.status, h.confirmedAt " +
           "FROM Help h WHERE h.status = 'CONFIRMED' AND (h.deleted = false OR h.deleted IS NULL)")
    List<Object[]> findConfirmedHelpGraphData();

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
