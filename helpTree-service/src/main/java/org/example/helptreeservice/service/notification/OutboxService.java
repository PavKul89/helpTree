package org.example.helptreeservice.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.entity.OutboxEvent;
import org.example.helptreeservice.enums.OutboxStatus;
import org.example.helptreeservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Durable outbox primitives.
 *
 * <p>Claiming and delivery are separate transactions on purpose: the row lock
 * is held only while the batch is marked {@code PROCESSING}, then released. A
 * slow SMTP server therefore cannot block other workers or hold a row lock for
 * the duration of a network call.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private static final List<String> DISPATCHABLE =
            List.of(OutboxStatus.PENDING.name(), OutboxStatus.FAILED.name());

    private final OutboxEventRepository outboxEventRepository;

    /** Inserts the durable intent in the caller's transaction. */
    @Transactional
    public OutboxEvent enqueue(OutboxEvent event) {
        return outboxEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxClaim> claimBatch(String workerId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = outboxEventRepository.lockDispatchableIds(DISPATCHABLE, now, limit);
        if (ids.isEmpty()) {
            return List.of();
        }

        List<OutboxEvent> events = outboxEventRepository.findAllById(ids);
        events.sort(Comparator.comparing(OutboxEvent::getId));
        List<OutboxClaim> claims = new ArrayList<>(events.size());
        for (OutboxEvent event : events) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setAttempts((event.getAttempts() == null ? 0 : event.getAttempts()) + 1);
            event.setLockedAt(now);
            event.setLockedBy(workerId);
            event.setLastError(null);
            claims.add(new OutboxClaim(event.getId(), event.getAttempts()));
        }
        outboxEventRepository.saveAll(events);
        log.debug("Захвачено {} outbox-событий воркером {}", claims.size(), workerId);
        return claims;
    }

    /**
     * Tries to claim one event right after the creating transaction commits. If
     * another worker (or the scheduler) already owns it, the method returns
     * {@code null} and the event is left to that worker.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxClaim claimById(String workerId, Long eventId) {
        LocalDateTime now = LocalDateTime.now();
        boolean claimed = outboxEventRepository
                .lockDispatchableId(eventId, DISPATCHABLE, now)
                .isPresent();
        if (!claimed) {
            return null;
        }

        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return null;
        }
        event.setStatus(OutboxStatus.PROCESSING);
        event.setAttempts((event.getAttempts() == null ? 0 : event.getAttempts()) + 1);
        event.setLockedAt(now);
        event.setLockedBy(workerId);
        event.setLastError(null);
        outboxEventRepository.save(event);
        return new OutboxClaim(event.getId(), event.getAttempts());
    }

    /**
     * Returns abandoned PROCESSING rows to the retry pool. Called by the
     * scheduler before each sweep, so a crashed worker cannot strand an event.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int releaseStaleClaims(LocalDateTime threshold) {
        int released = outboxEventRepository.releaseStaleClaims(
                OutboxStatus.PROCESSING,
                OutboxStatus.FAILED,
                threshold,
                LocalDateTime.now(),
                "Превышено время обработки, событие возвращено в очередь");
        if (released > 0) {
            log.warn("Возвращено в очередь {} зависших outbox-событий", released);
        }
        return released;
    }

    public record OutboxClaim(Long eventId, int attempts) {
    }
}
