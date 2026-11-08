package org.example.helptreeservice.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.NotificationProperties;
import org.example.helptreeservice.config.FeatureFlags;
import org.example.helptreeservice.enums.FeatureFlag;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Drives the outbox. The scheduler sweeps in bounded batches; the creating
 * transaction also attempts an immediate dispatch after commit, so the common
 * case does not wait for the next tick. Both paths claim rows the same way, so
 * they cannot deliver the same event twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {

    private final OutboxService outboxService;
    private final OutboxDeliveryService deliveryService;
    private final NotificationProperties properties;
    private final FeatureFlags featureFlags;

    private final String workerId = "notif-" + UUID.randomUUID().toString().substring(0, 8);

    /** Claim and deliver up to {@code maxBatchesPerRun} bounded batches. */
    public void dispatchDue() {
        if (featureFlags.isDisabled(FeatureFlag.NOTIFICATIONS)) {
            return;
        }

        LocalDateTime staleThreshold = LocalDateTime.now()
                .minusMinutes(Math.max(1, properties.getProcessingTimeoutMinutes()));
        outboxService.releaseStaleClaims(staleThreshold);

        int batchSize = Math.max(1, properties.getBatchSize());
        int maxBatches = Math.max(1, properties.getMaxBatchesPerRun());
        int delivered = 0;

        for (int round = 0; round < maxBatches; round++) {
            List<OutboxService.OutboxClaim> claims = outboxService.claimBatch(workerId, batchSize);
            if (claims.isEmpty()) {
                break;
            }
            for (OutboxService.OutboxClaim claim : claims) {
                try {
                    deliveryService.deliver(claim.eventId());
                    delivered++;
                } catch (RuntimeException e) {
                    // The delivery service normally converts failures into a
                    // FAILED/DEAD status; this is only a last-resort guard.
                    log.error("Необработанная ошибка доставки outbox-события {}",
                            claim.eventId(), e);
                }
            }
            if (claims.size() < batchSize) {
                break;
            }
        }

        if (delivered > 0) {
            log.debug("Outbox: обработано {} событий", delivered);
        }
    }

    /** Best-effort immediate attempt after the creating transaction commits. */
    public void dispatchImmediately(Long eventId) {
        if (featureFlags.isDisabled(FeatureFlag.NOTIFICATIONS) || eventId == null) {
            return;
        }
        try {
            OutboxService.OutboxClaim claim = outboxService.claimById(workerId, eventId);
            if (claim != null) {
                deliveryService.deliver(claim.eventId());
            }
        } catch (RuntimeException e) {
            log.warn("Не удалось немедленно доставить outbox-событие {}: {}",
                    eventId, e.getMessage());
        }
    }
}
