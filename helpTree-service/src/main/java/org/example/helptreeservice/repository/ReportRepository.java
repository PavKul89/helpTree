package org.example.helptreeservice.repository;

import jakarta.persistence.LockModeType;
import org.example.helptreeservice.entity.Report;
import org.example.helptreeservice.enums.ReportPriority;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
            Long reporterId,
            ReportTargetType targetType,
            Long targetId,
            Collection<ReportStatus> statuses);

    List<Report> findByTargetTypeAndTargetIdAndStatusIn(
            ReportTargetType targetType,
            Long targetId,
            Collection<ReportStatus> statuses);

    Optional<Report> findByReporterIdAndIdempotencyKey(Long reporterId, String idempotencyKey);

    Optional<Report> findByIdAndReporterId(Long id, Long reporterId);

    @EntityGraph(attributePaths = {"reporter", "assignedModerator", "resolvedBy", "duplicateOf"})
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> findByIdWithDetails(@Param("id") Long id);

    /**
     * Serializes decisions on one report. Without the row lock two
     * administrators could resolve the same report concurrently and write two
     * conflicting actions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"reporter", "assignedModerator", "resolvedBy", "duplicateOf"})
    @Query(value = """
            SELECT r FROM Report r
            LEFT JOIN r.reporter reporter
            LEFT JOIN r.assignedModerator assigned
            LEFT JOIN r.resolvedBy resolved
            LEFT JOIN r.duplicateOf duplicate
            WHERE r.reporter.id = :reporterId
              AND (:status IS NULL OR r.status = :status)
              AND (:targetType IS NULL OR r.targetType = :targetType)
              AND (:reason IS NULL OR r.reason = :reason)
            ORDER BY r.createdAt DESC, r.id DESC
            """,
           countQuery = """
            SELECT COUNT(r) FROM Report r
            WHERE r.reporter.id = :reporterId
              AND (:status IS NULL OR r.status = :status)
              AND (:targetType IS NULL OR r.targetType = :targetType)
              AND (:reason IS NULL OR r.reason = :reason)
            """)
    Page<Report> findMine(@Param("reporterId") Long reporterId,
                          @Param("status") ReportStatus status,
                          @Param("targetType") ReportTargetType targetType,
                          @Param("reason") ReportReason reason,
                          Pageable pageable);

    /**
     * Administrative queue. Every filter is optional and applied in the
     * database; the text search covers the reporter's name and the public
     * description, never the staff-only internal note.
     */
    @EntityGraph(attributePaths = {"reporter", "assignedModerator", "resolvedBy", "duplicateOf"})
    @Query(value = """
            SELECT r FROM Report r
            LEFT JOIN r.reporter reporter
            LEFT JOIN r.assignedModerator assigned
            LEFT JOIN r.resolvedBy resolved
            LEFT JOIN r.duplicateOf duplicate
            WHERE (:status IS NULL OR r.status = :status)
              AND (:targetType IS NULL OR r.targetType = :targetType)
              AND (:reason IS NULL OR r.reason = :reason)
              AND (:priority IS NULL OR r.priority = :priority)
              AND (:assignedModeratorId IS NULL OR assigned.id = :assignedModeratorId)
              AND (:reporterId IS NULL OR reporter.id = :reporterId)
              AND (:from IS NULL OR r.createdAt >= :from)
              AND (:to IS NULL OR r.createdAt <= :to)
              AND (:q IS NULL OR lower(reporter.name) LIKE :q OR lower(r.description) LIKE :q)
            ORDER BY
              CASE r.priority
                WHEN org.example.helptreeservice.enums.ReportPriority.URGENT THEN 0
                WHEN org.example.helptreeservice.enums.ReportPriority.HIGH THEN 1
                WHEN org.example.helptreeservice.enums.ReportPriority.NORMAL THEN 2
                ELSE 3
              END,
              r.createdAt ASC,
              r.id ASC
            """,
           countQuery = """
            SELECT COUNT(r) FROM Report r
            LEFT JOIN r.reporter reporter
            LEFT JOIN r.assignedModerator assigned
            WHERE (:status IS NULL OR r.status = :status)
              AND (:targetType IS NULL OR r.targetType = :targetType)
              AND (:reason IS NULL OR r.reason = :reason)
              AND (:priority IS NULL OR r.priority = :priority)
              AND (:assignedModeratorId IS NULL OR assigned.id = :assignedModeratorId)
              AND (:reporterId IS NULL OR reporter.id = :reporterId)
              AND (:from IS NULL OR r.createdAt >= :from)
              AND (:to IS NULL OR r.createdAt <= :to)
              AND (:q IS NULL OR lower(reporter.name) LIKE :q OR lower(r.description) LIKE :q)
            """)
    Page<Report> findForAdmin(@Param("status") ReportStatus status,
                              @Param("targetType") ReportTargetType targetType,
                              @Param("reason") ReportReason reason,
                              @Param("priority") ReportPriority priority,
                              @Param("assignedModeratorId") Long assignedModeratorId,
                              @Param("reporterId") Long reporterId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              @Param("q") String q,
                              Pageable pageable);
}
