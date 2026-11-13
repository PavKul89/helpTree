package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.OutboxEvent;
import org.example.helptreeservice.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims a batch of dispatchable rows with {@code FOR UPDATE SKIP LOCKED}.
     * The lock is held only for the short claim transaction; delivery happens in
     * a separate transaction. {@code SKIP LOCKED} lets multiple application
     * instances work on disjoint batches without waiting on each other.
     */
    @Query(value = "SELECT id FROM outbox_events "
            + "WHERE status IN (:statuses) AND available_at <= :now "
            + "ORDER BY available_at, id "
            + "LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<Long> lockDispatchableIds(@Param("statuses") List<String> statuses,
                                   @Param("now") LocalDateTime now,
                                   @Param("limit") int limit);

    /** Claim one specific event after its creating transaction commits. */
    @Query(value = "SELECT id FROM outbox_events "
            + "WHERE id = :id AND status IN (:statuses) AND available_at <= :now "
            + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<Long> lockDispatchableId(@Param("id") Long id,
                                      @Param("statuses") List<String> statuses,
                                      @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);

    Optional<OutboxEvent> findByDedupeKey(String dedupeKey);

    /**
     * A process can die after claiming an event and before marking it published.
     * Such rows would otherwise stay PROCESSING forever. The scheduler returns
     * them to the retry pool; the attempt has already been counted at claim time.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OutboxEvent e SET e.status = :failed, e.availableAt = :now, "
            + "e.lockedAt = NULL, e.lockedBy = NULL, "
            + "e.lastError = :reason "
            + "WHERE e.status = :processing AND e.lockedAt < :threshold")
    int releaseStaleClaims(@Param("processing") OutboxStatus processing,
                           @Param("failed") OutboxStatus failed,
                           @Param("threshold") LocalDateTime threshold,
                           @Param("now") LocalDateTime now,
                           @Param("reason") String reason);

    long countByStatus(OutboxStatus status);

    @Modifying
    @Transactional
    @Query(value = "WITH expired AS ("
            + "SELECT id, created_at FROM outbox_events "
            + "WHERE status = 'PUBLISHED' AND published_at < :cutoff "
            + "ORDER BY published_at, id LIMIT :batchSize FOR UPDATE SKIP LOCKED"
            + ") DELETE FROM outbox_events e USING expired "
            + "WHERE e.id = expired.id AND e.created_at = expired.created_at",
            nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") LocalDateTime cutoff,
                              @Param("batchSize") int batchSize);

    long countByStatusIn(Collection<OutboxStatus> statuses);

    /** Age reference for the backlog metric: the oldest event that is not published. */
    @Query("SELECT MIN(e.createdAt) FROM OutboxEvent e WHERE e.status IN :statuses")
    LocalDateTime findOldestCreatedAtByStatusIn(@Param("statuses") Collection<OutboxStatus> statuses);
}
