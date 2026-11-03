package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.dto.wallet.BalanceAnomalyDto;
import org.example.helptreeservice.event.BalanceAnomalyEvent;
import org.example.helptreeservice.repository.CoinTransactionRepository;
import org.example.helptreeservice.repository.projection.BalanceMismatchProjection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only reconciliation between users.help_coins and the ledger sum.
 *
 * <p>It deliberately does not modify balances: an automatic "repair" could
 * create coins if the ledger itself is wrong. Operators get an ERROR log, an
 * admin endpoint and a {@link BalanceAnomalyEvent} hook for future notification
 * channels.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletReconciliationService {

    private final CoinTransactionRepository transactionRepository;
    private final EconomyConfig economyConfig;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<BalanceAnomalyDto> findAnomalies() {
        LocalDateTime now = LocalDateTime.now();
        return transactionRepository.findBalanceMismatches().stream()
                .map(projection -> toDto(projection, now))
                .toList();
    }

    @Scheduled(cron = "${app.economy.reconciliation-cron:0 0 4 * * *}")
    public void reconcile() {
        if (!economyConfig.isReconciliationEnabled()) {
            return;
        }

        List<BalanceAnomalyDto> anomalies = findAnomalies();
        if (anomalies.isEmpty()) {
            log.debug("Сверка кошельков: расхождений нет");
            return;
        }

        for (BalanceAnomalyDto anomaly : anomalies) {
            log.error("WALLET ANOMALY: userId={} balance={} ledger={} difference={}",
                    anomaly.getUserId(),
                    anomaly.getBalance(),
                    anomaly.getLedgerTotal(),
                    anomaly.getDifference());
            eventPublisher.publishEvent(new BalanceAnomalyEvent(
                    anomaly.getUserId(),
                    anomaly.getBalance(),
                    anomaly.getLedgerTotal(),
                    anomaly.getDifference(),
                    anomaly.getDetectedAt()));
        }

        log.error("Сверка кошельков: обнаружено {} расхождений", anomalies.size());
    }

    private BalanceAnomalyDto toDto(BalanceMismatchProjection projection, LocalDateTime detectedAt) {
        long balance = projection.getBalance() != null ? projection.getBalance() : 0L;
        long ledger = projection.getLedgerTotal() != null ? projection.getLedgerTotal() : 0L;
        return BalanceAnomalyDto.builder()
                .userId(projection.getUserId())
                .balance(balance)
                .ledgerTotal(ledger)
                .difference(balance - ledger)
                .detectedAt(detectedAt)
                .build();
    }
}
