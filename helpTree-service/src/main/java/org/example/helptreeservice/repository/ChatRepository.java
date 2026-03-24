package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    @Query("SELECT c FROM Chat c WHERE c.user1.id = :userId OR c.user2.id = :userId ORDER BY c.lastMessageAt DESC NULLS LAST")
    List<Chat> findByUserId(@Param("userId") Long userId);

    @Query("SELECT c FROM Chat c WHERE (c.user1.id = :user1 AND c.user2.id = :user2) OR (c.user1.id = :user2 AND c.user2.id = :user1)")
    Optional<Chat> findByUsers(@Param("user1") Long user1, @Param("user2") Long user2);
}
