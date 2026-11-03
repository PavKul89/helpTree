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
}
