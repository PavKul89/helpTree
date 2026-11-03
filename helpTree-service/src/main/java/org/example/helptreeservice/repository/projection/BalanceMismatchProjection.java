package org.example.helptreeservice.repository.projection;

/**
 * Native-query projection for users whose stored balance differs from the sum
 * of their ledger rows.
 */
public interface BalanceMismatchProjection {
    Long getUserId();

    Long getBalance();

    Long getLedgerTotal();
}
