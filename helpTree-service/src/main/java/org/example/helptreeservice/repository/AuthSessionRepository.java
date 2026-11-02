package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AuthSession s WHERE s.id = :sessionId")
    Optional<AuthSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

    @Query("SELECT s FROM AuthSession s WHERE s.id = :sessionId AND s.user.id = :userId")
    Optional<AuthSession> findByIdAndUserId(@Param("sessionId") UUID sessionId,
                                            @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AuthSession s WHERE s.id = :sessionId AND s.user.id = :userId")
    Optional<AuthSession> findByIdAndUserIdForUpdate(@Param("sessionId") UUID sessionId,
                                                     @Param("userId") Long userId);

    @Query("SELECT s FROM AuthSession s WHERE s.id = :sessionId AND s.revokedAt IS NULL " +
           "AND s.expiresAt > :now")
    Optional<AuthSession> findActiveById(@Param("sessionId") UUID sessionId,
                                         @Param("now") LocalDateTime now);

    List<AuthSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE AuthSession s SET s.revokedAt = :now, s.revokedReason = :reason " +
           "WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    int revokeAllActiveByUserId(@Param("userId") Long userId,
                                @Param("now") LocalDateTime now,
                                @Param("reason") String reason);

    @Modifying
    @Query("DELETE FROM AuthSession s WHERE s.expiresAt < :now OR " +
           "(s.revokedAt IS NOT NULL AND s.revokedAt < :revokedCutoff)")
    int deleteExpiredOrOldRevoked(@Param("now") LocalDateTime now,
                                  @Param("revokedCutoff") LocalDateTime revokedCutoff);
}
