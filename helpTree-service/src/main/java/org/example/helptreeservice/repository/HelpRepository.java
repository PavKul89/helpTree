package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface HelpRepository extends JpaRepository<Help, Long> {

    List<Help> findByPost(Post post);

    @Query("SELECT h FROM Help h JOIN FETCH h.post JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.post.id = :postId AND (h.deleted = false OR h.deleted IS NULL)")
    List<Help> findByPostId(@Param("postId") Long postId);

    List<Help> findByHelper(User helper);

    List<Help> findByReceiver(User receiver);

    @Query("SELECT h FROM Help h JOIN FETCH h.post JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.receiver = :receiver AND (h.deleted = false OR h.deleted IS NULL)")
    List<Help> findByReceiverWithDetails(@Param("receiver") User receiver);

    @Query("SELECT h FROM Help h JOIN FETCH h.post JOIN FETCH h.helper JOIN FETCH h.receiver WHERE h.helper = :helper AND (h.deleted = false OR h.deleted IS NULL)")
    List<Help> findByHelperWithDetails(@Param("helper") User helper);

    Optional<Help> findByPostAndStatusNot(Post post, HelpStatus status);

    boolean existsByPostAndHelper(Post post, User helper);

    List<Help> findByStatus(HelpStatus status);
}
