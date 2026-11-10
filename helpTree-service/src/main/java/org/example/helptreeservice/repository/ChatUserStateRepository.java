package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.ChatUserState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatUserStateRepository extends JpaRepository<ChatUserState, Long> {

    Optional<ChatUserState> findByChatIdAndUserId(Long chatId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ChatUserState s WHERE s.chat.id = :chatId AND s.user.id = :userId")
    Optional<ChatUserState> findByChatIdAndUserIdForUpdate(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId);

    List<ChatUserState> findByUserIdAndDeletedAtIsNull(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatUserState s SET s.deletedAt = :deletedAt, s.updatedAt = :deletedAt "
            + "WHERE s.chat.id = :chatId AND s.user.id = :userId")
    int markDeleted(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("deletedAt") LocalDateTime deletedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatUserState s SET s.deletedAt = NULL, s.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE s.chat.id = :chatId AND s.user.id = :userId AND s.deletedAt IS NOT NULL")
    int restore(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatUserState s SET s.lastReadMessageId = :messageId, s.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE s.chat.id = :chatId AND s.user.id = :userId "
            + "AND (s.lastReadMessageId IS NULL OR s.lastReadMessageId < :messageId)")
    int markReadUpTo(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId);

    /**
     * Retention sweep: a cursor that points at a physically removed message must
     * be cleared, otherwise unread counts would be computed from a missing id.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatUserState s SET s.lastReadMessageId = NULL "
            + "WHERE s.lastReadMessageId IN :messageIds")
    int clearLastReadMessageIds(@Param("messageIds") Collection<Long> messageIds);
}
