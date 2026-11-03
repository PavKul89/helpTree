package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByIdAndDeletedFalse(Long id);
    boolean existsByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    /**
     * Locks both participants in a deterministic id order. Chat creation uses this
     * to serialize concurrent requests for the same (and reverse) pair, which makes
     * the normalized unique index a backstop rather than the primary mechanism.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id IN :ids ORDER BY u.id")
    List<User> findAllByIdInForUpdate(@Param("ids") List<Long> ids);

    List<User> findByDeletedFalse();

    Page<User> findByDeletedFalse(Pageable pageable);

    @Modifying
    @Query("UPDATE User u SET u.helpedCount = u.helpedCount + 1, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :userId")
    void incrementHelpedCount(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE User u SET u.debtCount = u.debtCount + :delta, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :userId")
    void updateDebtCount(@Param("userId") Long userId, @Param("delta") int delta);

    @Query("SELECT u.id FROM User u WHERE u.id = :userId")
    Long findUserIdOnly(@Param("userId") Long userId);

    @Query(value = "SELECT post_id FROM user_favorites WHERE user_id = :userId", nativeQuery = true)
    List<Long> findFavoritePostIds(@Param("userId") Long userId);

    @Query("SELECT u FROM User u WHERE u.deleted = false AND u.debtCount > 5 AND (u.blockedUntil IS NULL OR u.blockedUntil < :now)")
    List<User> findUsersWithDebtToBlock(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deleted = false OR u.deleted IS NULL")
    long countActive();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND (u.deleted = false OR u.deleted IS NULL)")
    long countActiveByRole(@Param("role") Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.role = :role AND (u.deleted = false OR u.deleted IS NULL) ORDER BY u.id")
    List<User> findActiveByRoleForUpdate(@Param("role") Role role);

    @Modifying
    @Query("UPDATE User u SET u.blockedAt = NULL, u.blockedUntil = NULL, u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.deleted = false AND u.blockedUntil IS NOT NULL AND u.blockedUntil < :now")
    int unblockExpiredUsers(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE User u SET u.helpCoins = u.helpCoins - :amount, u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.id = :userId AND u.helpCoins >= :amount")
    int deductHelpCoins(@Param("userId") Long userId, @Param("amount") long amount);
}
