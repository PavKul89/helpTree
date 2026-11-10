package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.CoinTransaction;
import org.example.helptreeservice.enums.TransactionType;
import org.example.helptreeservice.repository.projection.BalanceMismatchProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, Long>,
        JpaSpecificationExecutor<CoinTransaction> {

    Page<CoinTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Complete ledger history for the portable account export. */
    List<CoinTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<CoinTransaction> findByUserIdAndTypeAndReferenceId(
            Long userId, TransactionType type, String referenceId);

    boolean existsByUserIdAndTypeAndCreatedAtAfter(Long userId, TransactionType type, LocalDateTime after);

    @Query("SELECT COALESCE(SUM(ct.amount), 0) FROM CoinTransaction ct " +
           "WHERE ct.userId = :userId AND ct.amount > 0")
    Long getTotalEarned(@Param("userId") Long userId);

    @Query("SELECT COUNT(ct) FROM CoinTransaction ct WHERE ct.userId = :userId AND ct.type = 'FIRST_HELP'")
    Long countFirstHelpBonuses(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(-ct.amount), 0) FROM CoinTransaction ct " +
           "WHERE ct.userId = :userId AND ct.amount < 0")
    Long getTotalSpent(@Param("userId") Long userId);

    @Query(value = """
            SELECT u.id AS "userId",
                   u.help_coins AS "balance",
                   COALESCE(SUM(ct.amount), 0) AS "ledgerTotal"
            FROM users u
            LEFT JOIN coin_transactions ct ON ct.user_id = u.id
            WHERE COALESCE(u.deleted, FALSE) = FALSE
            GROUP BY u.id, u.help_coins
            HAVING u.help_coins <> COALESCE(SUM(ct.amount), 0)
            ORDER BY u.id
            """, nativeQuery = true)
    List<BalanceMismatchProjection> findBalanceMismatches();

    /**
     * Paginated variant for the admin endpoint. The scheduled reconciliation
     * still uses the unpaginated list because it must log every anomaly.
     */
    @Query(value = """
            SELECT u.id AS "userId",
                   u.help_coins AS "balance",
                   COALESCE(SUM(ct.amount), 0) AS "ledgerTotal"
            FROM users u
            LEFT JOIN coin_transactions ct ON ct.user_id = u.id
            WHERE COALESCE(u.deleted, FALSE) = FALSE
            GROUP BY u.id, u.help_coins
            HAVING u.help_coins <> COALESCE(SUM(ct.amount), 0)
            ORDER BY u.id
            """, countQuery = """
            SELECT COUNT(*)
            FROM (
                SELECT u.id
                FROM users u
                LEFT JOIN coin_transactions ct ON ct.user_id = u.id
                WHERE COALESCE(u.deleted, FALSE) = FALSE
                GROUP BY u.id, u.help_coins
                HAVING u.help_coins <> COALESCE(SUM(ct.amount), 0)
            ) mismatches
            """, nativeQuery = true)
    Page<BalanceMismatchProjection> findBalanceMismatches(Pageable pageable);

    /** Count-only variant used by the operational gauge; it does not load rows. */
    @Query(value = """
            SELECT COUNT(*)
            FROM (
                SELECT u.id
                FROM users u
                LEFT JOIN coin_transactions ct ON ct.user_id = u.id
                WHERE COALESCE(u.deleted, FALSE) = FALSE
                GROUP BY u.id, u.help_coins
                HAVING u.help_coins <> COALESCE(SUM(ct.amount), 0)
            ) mismatches
            """, nativeQuery = true)
    long countBalanceMismatches();
}
