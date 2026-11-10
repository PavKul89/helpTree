package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.PostView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface PostViewRepository extends JpaRepository<PostView, Long> {

    /**
     * Records one view for the day. The unique index
     * {@code uq_post_views_daily} makes this idempotent even when two requests
     * from the same viewer race; the returned row count tells the caller
     * whether the post counter must be incremented.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO post_views (post_id, viewer_key, view_date, created_at)
            VALUES (:postId, :viewerKey, :viewDate, CURRENT_TIMESTAMP)
            ON CONFLICT (post_id, viewer_key, view_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("postId") Long postId,
                       @Param("viewerKey") String viewerKey,
                       @Param("viewDate") LocalDate viewDate);
}
