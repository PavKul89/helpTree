package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.CoinTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, Long> {
    
    Page<CoinTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    List<CoinTransaction> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);
    
    @Query("SELECT COALESCE(SUM(ct.amount), 0) FROM CoinTransaction ct WHERE ct.userId = :userId AND ct.type IN ('HELP_GIVEN', 'HELP_RECEIVED', 'REVIEW_BONUS', 'DAILY_LOGIN', 'ADMIN_BONUS', 'GIFT_RECEIVED')")
    Long getTotalEarned(@Param("userId") Long userId);
    
    @Query("SELECT COALESCE(SUM(ABS(ct.amount)), 0) FROM CoinTransaction ct WHERE ct.userId = :userId AND ct.amount < 0")
    Long getTotalSpent(@Param("userId") Long userId);
}
