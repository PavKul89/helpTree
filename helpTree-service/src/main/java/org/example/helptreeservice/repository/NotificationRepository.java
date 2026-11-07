package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Idempotent insert backed by the partial unique index
     * {@code uq_notifications_user_dedupe}. A conflict is not an error: the
     * caller treats {@code 0} as "the notification already exists" and neither
     * pushes a WebSocket event nor enqueues a second delivery.
     *
     * <p>The target must repeat the index predicate ({@code dedupe_key IS NOT
     * NULL}) so PostgreSQL can select the partial index.</p>
     */
    @Modifying
    @Query(value = "INSERT INTO notifications "
            + "(user_id, type, title, body, payload_json, dedupe_key, created_at) "
            + "VALUES (:userId, :type, :title, :body, CAST(:payloadJson AS jsonb), "
            + ":dedupeKey, :createdAt) "
            + "ON CONFLICT (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("type") String type,
                       @Param("title") String title,
                       @Param("body") String body,
                       @Param("payloadJson") String payloadJson,
                       @Param("dedupeKey") String dedupeKey,
                       @Param("createdAt") LocalDateTime createdAt);

    Optional<Notification> findByUserIdAndDedupeKey(Long userId, String dedupeKey);

    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(
            Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :readAt "
            + "WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL")
    int markRead(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("readAt") LocalDateTime readAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :readAt "
            + "WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId,
                    @Param("readAt") LocalDateTime readAt);
}
