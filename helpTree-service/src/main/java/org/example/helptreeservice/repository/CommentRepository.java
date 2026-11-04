package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Top-level comments are paginated independently from their replies. The
     * entity graph loads the author needed by the response mapper.
     */
    @EntityGraph(attributePaths = {"user"})
    @Query(value = "SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parentCommentId IS NULL",
           countQuery = "SELECT COUNT(c) FROM Comment c WHERE c.post.id = :postId AND c.parentCommentId IS NULL")
    Page<Comment> findRootComments(@Param("postId") Long postId, Pageable pageable);

    /**
     * Returns every comment belonging to the selected root threads, including
     * tombstones. A recursive query keeps a page self-contained: the client
     * never receives a reply whose parent is on a different page.
     */
    @Query(value = """
            WITH RECURSIVE comment_tree AS (
                SELECT c.id, c.id AS root_id
                FROM comments c
                WHERE c.post_id = :postId
                  AND c.parent_comment_id IS NULL
                  AND c.id IN (:rootIds)
                UNION ALL
                SELECT c.id, t.root_id
                FROM comments c
                JOIN comment_tree t ON c.parent_comment_id = t.id
                WHERE c.post_id = :postId
            )
            SELECT c.id
            FROM comments c
            JOIN comment_tree t ON t.id = c.id
            WHERE t.root_id IN (:rootIds)
            ORDER BY c.created_at ASC, c.id ASC
            """, nativeQuery = true)
    List<Long> findThreadCommentIds(@Param("postId") Long postId,
                                    @Param("rootIds") Collection<Long> rootIds);

    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.id IN :ids ORDER BY c.createdAt ASC, c.id ASC")
    List<Comment> findAllByIdInWithUser(@Param("ids") Collection<Long> ids);

    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.user.id = :userId AND c.deleted = false ORDER BY c.createdAt DESC")
    List<Comment> findByUserIdWithUser(@Param("userId") Long userId);

    long countByPostIdAndDeletedFalse(Long postId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);
}
