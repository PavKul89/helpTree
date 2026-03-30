package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c JOIN FETCH c.user LEFT JOIN FETCH c.post WHERE c.post.id = :postId ORDER BY c.createdAt ASC")
    List<Comment> findByPostIdOrderByCreatedAtAsc(@Param("postId") Long postId);

    @Query("SELECT c FROM Comment c JOIN FETCH c.user LEFT JOIN FETCH c.post WHERE c.user.id = :userId")
    List<Comment> findByUserId(@Param("userId") Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);
}
