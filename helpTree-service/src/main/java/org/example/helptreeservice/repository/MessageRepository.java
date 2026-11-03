package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findByChatIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long chatId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId "
            + "AND m.deletedAt IS NULL "
            + "AND (:beforeId IS NULL OR m.id < :beforeId) "
            + "ORDER BY m.id DESC")
    List<Message> findPageByCursor(
            @Param("chatId") Long chatId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    Optional<Message> findTopByChatIdAndDeletedAtIsNullOrderByIdDesc(Long chatId);

    @EntityGraph(attributePaths = {"sender"})
    Optional<Message> findByIdAndChatIdAndDeletedAtIsNull(Long id, Long chatId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Message m SET m.isRead = true "
            + "WHERE m.chat.id = :chatId AND m.sender.id <> :userId AND m.isRead = false")
    int markMessagesAsRead(@Param("chatId") Long chatId, @Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chat.id = :chatId "
            + "AND m.sender.id <> :userId AND m.deletedAt IS NULL "
            + "AND m.id > COALESCE(:lastReadMessageId, 0)")
    long countUnread(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("lastReadMessageId") Long lastReadMessageId);

    @Query("SELECT COALESCE(MAX(m.id), 0) FROM Message m "
            + "WHERE m.chat.id = :chatId AND m.deletedAt IS NULL")
    long findMaxActiveMessageId(@Param("chatId") Long chatId);

    long countBySenderId(Long senderId);
}
