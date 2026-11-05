package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.ImageObject;
import org.example.helptreeservice.enums.ImageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImageObjectRepository extends JpaRepository<ImageObject, Long> {

    Optional<ImageObject> findByObjectKey(String objectKey);

    Optional<ImageObject> findByObjectKeyAndStatus(String objectKey, ImageStatus status);

    Optional<ImageObject> findByUrl(String url);

    Optional<ImageObject> findByUrlAndStatus(String url, ImageStatus status);

    List<ImageObject> findAllByUrlInAndStatus(Collection<String> urls, ImageStatus status);

    boolean existsByObjectKey(String objectKey);

    boolean existsByUrl(String url);

    long countByOwnerIdAndStatus(Long ownerId, ImageStatus status);

    @Query("SELECT COALESCE(SUM(i.sizeBytes), 0) FROM ImageObject i " +
           "WHERE i.ownerId = :ownerId AND i.status = :status")
    long sumSizeByOwnerAndStatus(@Param("ownerId") Long ownerId,
                                 @Param("status") ImageStatus status);

    /**
     * True when any user avatar or post image still points at the URL. The
     * cleanup job uses the same predicate before it marks a row deletable.
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM users u WHERE u.avatar_url = :url)
                OR EXISTS (SELECT 1 FROM post_images pi WHERE pi.image_url = :url)
            """, nativeQuery = true)
    boolean existsReferenceByUrl(@Param("url") String url);

    /**
     * Active rows older than the TTL that are not referenced by a live profile
     * or post. Bounded by {@code limit}; the caller loops over batches.
     */
    @Query(value = """
            SELECT io.*
            FROM image_objects io
            WHERE io.status = 'ACTIVE'
              AND io.created_at <= :cutoff
              AND NOT EXISTS (SELECT 1 FROM users u WHERE u.avatar_url = io.url)
              AND NOT EXISTS (SELECT 1 FROM post_images pi WHERE pi.image_url = io.url)
            ORDER BY io.id
            LIMIT :limit
            """, nativeQuery = true)
    List<ImageObject> findUnreferencedActive(@Param("cutoff") LocalDateTime cutoff,
                                             @Param("limit") int limit);

    @Query(value = """
            SELECT io.*
            FROM image_objects io
            WHERE io.status = 'DELETED'
              AND io.deleted_at <= :cutoff
            ORDER BY io.id
            LIMIT :limit
            """, nativeQuery = true)
    List<ImageObject> findDeletedBefore(@Param("cutoff") LocalDateTime cutoff,
                                        @Param("limit") int limit);

    /**
     * Marks an object deleted only while it is still unreferenced and older
     * than the TTL. The reference checks live in the UPDATE so a post created
     * concurrently cannot lose its image.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE image_objects io
            SET status = 'DELETED', deleted_at = :now
            WHERE io.id = :id
              AND io.status = 'ACTIVE'
              AND io.created_at <= :cutoff
              AND NOT EXISTS (SELECT 1 FROM users u WHERE u.avatar_url = io.url)
              AND NOT EXISTS (SELECT 1 FROM post_images pi WHERE pi.image_url = io.url)
            """, nativeQuery = true)
    int markUnreferencedDeleted(@Param("id") Long id,
                                @Param("cutoff") LocalDateTime cutoff,
                                @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ImageObject i WHERE i.id = :id AND i.status = :status")
    int deleteDeletedById(@Param("id") Long id, @Param("status") ImageStatus status);
}
