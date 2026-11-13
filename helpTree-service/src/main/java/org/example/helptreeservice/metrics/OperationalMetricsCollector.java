package org.example.helptreeservice.metrics;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.enums.OutboxStatus;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.repository.CoinTransactionRepository;
import org.example.helptreeservice.repository.OutboxEventRepository;
import org.example.helptreeservice.repository.ReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refreshes the state gauges on a fixed cadence.
 *
 * <p>Prometheus scrapes, but the collector is the only component that runs the
 * aggregate queries. A scrape therefore costs one counter read, not a set of
 * full-table scans, and a slow query cannot delay a scrape.</p>
 *
 * <p>Every failure is contained: a database hiccup sets
 * {@code helptree.database.up=0} and logs once, while the previous gauge values
 * stay visible until the next successful refresh.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationalMetricsCollector {

    private static final List<ReportStatus> OPEN_STATUSES =
            List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW);
    private static final List<OutboxStatus> UNPUBLISHED_STATUSES =
            List.of(OutboxStatus.PENDING, OutboxStatus.PROCESSING, OutboxStatus.FAILED);

    private final ReportRepository reportRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CoinTransactionRepository coinTransactionRepository;
    private final HelpTreeMetrics metrics;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.reports.sla-hours:24}")
    private long reportSlaHours;

    private volatile boolean lastProbeFailed;
    private volatile long lastDeadOutboxCount = -1;

    @Scheduled(fixedDelayString = "${app.metrics.refresh-ms:60000}",
            initialDelayString = "${app.metrics.initial-delay-ms:15000}")
    @SchedulerLock(name = "OperationalMetricsCollector_refresh",
            lockAtMostFor = "PT10M", lockAtLeastFor = "PT0S")
    public void refresh() {
        probeDatabase();
        if (lastProbeFailed) {
            return;
        }
        try {
            refreshReportMetrics();
            refreshOutboxMetrics();
            refreshWalletMetrics();
        } catch (RuntimeException e) {
            log.warn("Operational metrics refresh failed: {}", e.getMessage());
        }
    }

    private void probeDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            metrics.setDatabaseUp(true);
            if (lastProbeFailed) {
                log.info("Database probe recovered");
            }
            lastProbeFailed = false;
        } catch (RuntimeException e) {
            metrics.setDatabaseUp(false);
            if (!lastProbeFailed) {
                log.error("Database probe failed: {}", e.getMessage());
            }
            lastProbeFailed = true;
        }
    }

    private void refreshReportMetrics() {
        Map<String, Long> byStatus = new ConcurrentHashMap<>();
        for (Object[] row : reportRepository.countByStatus()) {
            byStatus.put(String.valueOf(row[0]),
                    row[1] instanceof Number number ? number.longValue() : 0L);
        }
        long open = byStatus.getOrDefault(ReportStatus.OPEN.name(), 0L);
        long inReview = byStatus.getOrDefault(ReportStatus.IN_REVIEW.name(), 0L);
        metrics.setReportCounts(open, open + inReview);

        LocalDateTime threshold = LocalDateTime.now().minusHours(Math.max(1, reportSlaHours));
        metrics.setSlaBreaches(
                reportRepository.countByStatusInAndCreatedAtBefore(OPEN_STATUSES, threshold));
    }

    private void refreshOutboxMetrics() {
        long backlog = outboxEventRepository.countByStatusIn(UNPUBLISHED_STATUSES);
        LocalDateTime oldest = outboxEventRepository
                .findOldestCreatedAtByStatusIn(UNPUBLISHED_STATUSES);
        long ageSeconds = oldest == null
                ? 0
                : Math.max(0, Duration.between(oldest, LocalDateTime.now()).toSeconds());
        metrics.setOutboxState(backlog, ageSeconds);
        long dead = outboxEventRepository.countByStatus(OutboxStatus.DEAD);
        metrics.setOutboxDead(dead);
        if (dead > 0 && dead != lastDeadOutboxCount) {
            log.warn("В outbox находится {} событий DEAD, требуется ручной разбор", dead);
        }
        lastDeadOutboxCount = dead;
    }

    private void refreshWalletMetrics() {
        metrics.setWalletBalanceMismatches(coinTransactionRepository.countBalanceMismatches());
    }
}
