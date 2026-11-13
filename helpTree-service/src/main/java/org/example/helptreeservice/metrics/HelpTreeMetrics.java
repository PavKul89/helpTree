package org.example.helptreeservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.enums.FeatureFlag;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central registry of the application's operational metrics.
 *
 * <p>Counters describe events (a report was created, an upload failed, a login
 * was rejected); gauges describe the current state of a queue or an invariant
 * check. Gauges are backed by {@link AtomicLong}s owned by this bean, so the
 * scheduled collector can publish a new value without touching the registry or
 * creating a new meter on every scrape.</p>
 *
 * <p>Tag values are deliberately bounded: an action type, an outcome, a status
 * code or a matched URI pattern. User ids, email addresses and free-form text
 * are never tags, otherwise the series cardinality would grow without limit.</p>
 */
@Component
@RequiredArgsConstructor
public class HelpTreeMetrics {

    public static final String API_DURATION = "helptree.api.duration";
    public static final String API_RESPONSE_SIZE = "helptree.api.response.size";
    public static final String API_AUTHORIZATION_ERRORS = "helptree.api.authorization.errors";
    public static final String API_SERVER_ERRORS = "helptree.api.server.errors";

    private final MeterRegistry registry;

    private final AtomicLong openReports = new AtomicLong();
    private final AtomicLong slaBreachReports = new AtomicLong();
    private final AtomicLong moderationQueue = new AtomicLong();
    private final AtomicLong walletBalanceMismatches = new AtomicLong();
    private final AtomicLong outboxBacklog = new AtomicLong();
    private final AtomicLong outboxOldestAgeSeconds = new AtomicLong();
    private final AtomicLong outboxDead = new AtomicLong();
    private final AtomicLong databaseUp = new AtomicLong(1);
    private final AtomicLong webSocketSessions = new AtomicLong();

    @PostConstruct
    void registerGauges() {
        gauge("helptree.reports.open", "Current open reports", openReports);
        gauge("helptree.reports.sla.breaches", "Open reports older than the SLA", slaBreachReports);
        gauge("helptree.moderation.queue", "Reports waiting for moderation", moderationQueue);
        gauge("helptree.wallet.balance.mismatches", "Wallets whose ledger does not match", walletBalanceMismatches);
        gauge("helptree.outbox.backlog", "Undelivered outbox events", outboxBacklog);
        gauge("helptree.outbox.oldest.age.seconds", "Age of the oldest undelivered outbox event",
                outboxOldestAgeSeconds);
        gauge("helptree.outbox.dead", "Outbox events requiring manual review", outboxDead);
        gauge("helptree.database.up", "1 when the last database probe succeeded", databaseUp);
        gauge("helptree.websocket.sessions", "Currently connected WebSocket sessions", webSocketSessions);
    }

    private void gauge(String name, String description, AtomicLong value) {
        Gauge.builder(name, value, AtomicLong::doubleValue)
                .description(description)
                .register(registry);
    }

    // ------------------------------------------------------------------
    // Counters
    // ------------------------------------------------------------------

    public void reportCreated() {
        counter("helptree.reports.created").increment();
    }

    public void moderationAction(String actionType) {
        counter("helptree.moderation.actions", "action", safe(actionType)).increment();
    }

    public void loginSuccess() {
        counter("helptree.auth.login.success").increment();
    }

    public void loginFailure(String reason) {
        counter("helptree.auth.login.failures", "reason", safe(reason)).increment();
    }

    public void imageUpload(String purpose, String outcome) {
        counter("helptree.images.uploads",
                "purpose", safe(purpose), "outcome", safe(outcome)).increment();
    }

    public void outboxDelivered() {
        counter("helptree.outbox.delivered").increment();
    }

    public void outboxFailed() {
        counter("helptree.outbox.failed").increment();
    }

    public void featureDisabled(FeatureFlag feature) {
        counter("helptree.feature.disabled",
                "feature", feature == null ? "unknown" : feature.getCode()).increment();
    }

    public void webSocketSubscribed() {
        counter("helptree.websocket.subscriptions").increment();
    }

    public void webSocketDisconnectEvent() {
        counter("helptree.websocket.disconnections").increment();
    }

    // ------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------

    public void apiRequest(String method, String uri, int status,
                           long durationNanos, long responseBytes) {
        String safeMethod = safe(method);
        String safeUri = safe(uri);
        Timer.builder(API_DURATION)
                .description("HTTP request duration")
                .tags("method", safeMethod, "uri", safeUri, "status", statusGroup(status))
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        DistributionSummary.builder(API_RESPONSE_SIZE)
                .description("HTTP response size in bytes")
                .baseUnit("bytes")
                .tags("method", safeMethod, "uri", safeUri, "status", statusGroup(status))
                .register(registry)
                .record(Math.max(0, responseBytes));

        if (status == 401 || status == 403) {
            counter(API_AUTHORIZATION_ERRORS, "status", Integer.toString(status)).increment();
        }
        if (status >= 500) {
            counter(API_SERVER_ERRORS, "status", Integer.toString(status)).increment();
        }
    }

    private String statusGroup(int status) {
        if (status >= 500) {
            return "5xx";
        }
        if (status >= 400) {
            return "4xx";
        }
        if (status >= 300) {
            return "3xx";
        }
        return "2xx";
    }

    // ------------------------------------------------------------------
    // Gauges updated by OperationalMetricsCollector
    // ------------------------------------------------------------------

    public void setReportCounts(long open, long moderation) {
        openReports.set(open);
        moderationQueue.set(moderation);
    }

    public void setSlaBreaches(long value) {
        slaBreachReports.set(value);
    }

    public void setWalletBalanceMismatches(long value) {
        walletBalanceMismatches.set(value);
    }

    public void setOutboxState(long backlog, long oldestAgeSeconds) {
        outboxBacklog.set(backlog);
        outboxOldestAgeSeconds.set(oldestAgeSeconds);
    }

    public void setOutboxDead(long count) {
        outboxDead.set(count);
    }

    public void setDatabaseUp(boolean up) {
        databaseUp.set(up ? 1 : 0);
    }

    public void webSocketConnected() {
        webSocketSessions.incrementAndGet();
    }

    public void webSocketDisconnected() {
        webSocketSessions.updateAndGet(value -> Math.max(0, value - 1));
    }

    // ------------------------------------------------------------------

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
