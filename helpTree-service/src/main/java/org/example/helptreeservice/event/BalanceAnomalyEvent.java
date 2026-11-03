package org.example.helptreeservice.event;

import java.time.LocalDateTime;

/**
 * Published when the ledger and the stored balance disagree. There is no
 * notification channel in the project yet, so this event is the integration
 * point for future in-app/email/Telegram delivery; the reconciliation service
 * also writes an ERROR log for immediate operational visibility.
 */
public record BalanceAnomalyEvent(
        Long userId,
        long balance,
        long ledgerTotal,
        long difference,
        LocalDateTime detectedAt
) {
}
