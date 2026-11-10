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

import java.time.LocalDateTime;
import java.util.Collection;
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

    /** Full authored-message history for the portable account export. */
    @EntityGraph(attributePaths = {"chat"})
    @Query("SELECT m FROM Message m WHERE m.sender.id = :userId ORDER BY m.createdAt DESC, m.id DESC")
    List<Message> findAllForExportBySenderId(@Param("userId") Long userId);

    @Query("SELECT m FROM Message m WHERE m.createdAt < :cutoff ORDER BY m.createdAt ASC")
    List<Message> findExpiredMessages(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Message m WHERE m.id IN :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * True while any live message still references the URL. Attachment URLs are
     * stored as a JSON array, so the check uses jsonb expansion instead of a
     * column comparison.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM messages m
                WHERE m.deleted_at IS NULL
                  AND m.attachment_urls <> '[]'
                  AND EXISTS (
                      SELECT 1 FROM jsonb_array_elements_text(m.attachment_urls::jsonb) a
                      WHERE a = :url
                  )
            )
            """, nativeQuery = true)
    boolean existsActiveByAttachmentUrl(@Param("url") String url);
}
