package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Report;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

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

    @Query(value = """
            SELECT r FROM Report r
            LEFT JOIN FETCH r.reporter
            LEFT JOIN FETCH r.resolvedBy
            WHERE (:status IS NULL OR r.status = :status)
              AND (:targetType IS NULL OR r.targetType = :targetType)
            ORDER BY r.createdAt ASC
            """,
           countQuery = """
            SELECT COUNT(r) FROM Report r
            WHERE (:status IS NULL OR r.status = :status)
              AND (:targetType IS NULL OR r.targetType = :targetType)
            """)
    Page<Report> findForAdmin(@Param("status") ReportStatus status,
                              @Param("targetType") ReportTargetType targetType,
                              Pageable pageable);
}
