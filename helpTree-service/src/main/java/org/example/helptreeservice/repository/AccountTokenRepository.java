package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.AccountToken;
import org.example.helptreeservice.enums.AccountTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    Optional<AccountToken> findByTokenHash(String tokenHash);

    @Query("SELECT t.user.id FROM AccountToken t WHERE t.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AccountToken t WHERE t.tokenHash = :tokenHash")
    Optional<AccountToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * Atomically consumes a token. The conditional update guarantees that only one
     * concurrent request can ever use the same link, even without a long-lived lock.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE AccountToken t SET t.usedAt = :now " +
           "WHERE t.tokenHash = :tokenHash AND t.type = :type " +
           "AND t.usedAt IS NULL AND t.revokedAt IS NULL AND t.expiresAt > :now")
    int consume(@Param("tokenHash") String tokenHash,
                @Param("type") AccountTokenType type,
                @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE AccountToken t SET t.revokedAt = :now " +
           "WHERE t.user.id = :userId AND t.type = :type " +
           "AND t.usedAt IS NULL AND t.revokedAt IS NULL")
    int revokeActiveByUserAndType(@Param("userId") Long userId,
                                  @Param("type") AccountTokenType type,
                                  @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM AccountToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
