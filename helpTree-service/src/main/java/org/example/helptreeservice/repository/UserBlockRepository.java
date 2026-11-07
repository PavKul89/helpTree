package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.UserBlock;
import org.example.helptreeservice.enums.UserBlockSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    @EntityGraph(attributePaths = {"user", "blockedBy", "liftedBy"})
    List<UserBlock> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            SELECT b FROM UserBlock b
            WHERE b.user.id = :userId
              AND b.source = :source
              AND b.liftedAt IS NULL
              AND (b.endsAt IS NULL OR b.endsAt > :now)
            ORDER BY b.createdAt DESC
            """)
    List<UserBlock> findActive(@Param("userId") Long userId,
                               @Param("source") UserBlockSource source,
                               @Param("now") LocalDateTime now);
}
