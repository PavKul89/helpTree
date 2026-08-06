package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
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

    @Query("SELECT h FROM Help h JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.helper = :helper AND (h.deleted = false OR h.deleted IS NULL)")
    Page<Help> findByHelperWithDetails(@Param("helper") User helper, Pageable pageable);

    @Query("SELECT h FROM Help h JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.receiver = :receiver AND (h.deleted = false OR h.deleted IS NULL)")
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
}
