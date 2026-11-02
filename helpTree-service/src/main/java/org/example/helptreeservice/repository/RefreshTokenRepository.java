package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("SELECT rt.session.id FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<UUID> findSessionIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now, rt.revokedReason = :reason " +
           "WHERE rt.session.id = :sessionId AND rt.revokedAt IS NULL")
    int revokeAllBySessionId(@Param("sessionId") UUID sessionId,
                             @Param("now") LocalDateTime now,
                             @Param("reason") String reason);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now, rt.revokedReason = :reason " +
           "WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId,
                          @Param("now") LocalDateTime now,
                          @Param("reason") String reason);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
