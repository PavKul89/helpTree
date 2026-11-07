package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.NotificationPreference;
import org.example.helptreeservice.enums.NotificationChannel;
import org.example.helptreeservice.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByUserId(Long userId);

    Optional<NotificationPreference> findByUserIdAndTypeAndChannel(
            Long userId, NotificationType type, NotificationChannel channel);

    /**
     * Concurrent settings screens must not lose an update or fail with a unique
     * violation. The unique constraint {@code (user_id, type, channel)} is the
     * conflict target of a server-side upsert.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO notification_preferences "
            + "(user_id, type, channel, enabled, created_at, updated_at) "
            + "VALUES (:userId, :type, :channel, :enabled, :now, :now) "
            + "ON CONFLICT (user_id, type, channel) "
            + "DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at",
            nativeQuery = true)
    int upsert(@Param("userId") Long userId,
               @Param("type") String type,
               @Param("channel") String channel,
               @Param("enabled") boolean enabled,
               @Param("now") LocalDateTime now);
}
