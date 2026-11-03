package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Chat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    @EntityGraph(attributePaths = {"user1", "user2"})
    @Query("SELECT c FROM Chat c WHERE c.user1.id = :userId OR c.user2.id = :userId "
            + "ORDER BY c.lastMessageAt DESC NULLS LAST")
    List<Chat> findByUserId(@Param("userId") Long userId);

    /**
     * Returns only chats that the given user has not hidden. The row is joined to
     * chat_user_state so a per-user deletion never affects the other participant.
     */
    @EntityGraph(attributePaths = {"user1", "user2"})
    @Query("SELECT c FROM Chat c WHERE c.id IN ("
            + "SELECT s.chat.id FROM ChatUserState s "
            + "WHERE s.user.id = :userId AND s.deletedAt IS NULL) "
            + "ORDER BY c.lastMessageAt DESC NULLS LAST")
    List<Chat> findVisibleByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user1", "user2"})
    @Query("SELECT c FROM Chat c WHERE (c.user1.id = :user1 AND c.user2.id = :user2) "
            + "OR (c.user1.id = :user2 AND c.user2.id = :user1)")
    Optional<Chat> findByUsers(@Param("user1") Long user1, @Param("user2") Long user2);

    @Query("SELECT COUNT(c) > 0 FROM Chat c WHERE c.id = :chatId "
            + "AND (c.user1.id = :userId OR c.user2.id = :userId)")
    boolean existsByIdAndParticipant(@Param("chatId") Long chatId, @Param("userId") Long userId);
}
