package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AdminAuditLogRepository
        extends JpaRepository<AdminAuditLog, Long>, JpaSpecificationExecutor<AdminAuditLog> {

    Page<AdminAuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, Long entityId, Pageable pageable);

    Page<AdminAuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);

    /**
     * Aggregates for the audit dashboard. Timestamps are always supplied by the
     * caller (a bounded window), so the native query never receives a null
     * timestamp parameter whose type PostgreSQL would have to infer.
     */
    @Query(value = """
            SELECT action, COUNT(*)
            FROM admin_audit_log
            WHERE created_at >= :from AND created_at < :to
            GROUP BY action
            ORDER BY COUNT(*) DESC, action ASC
            """, nativeQuery = true)
    List<Object[]> countByAction(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT entity_type, COUNT(*)
            FROM admin_audit_log
            WHERE created_at >= :from AND created_at < :to
            GROUP BY entity_type
            ORDER BY COUNT(*) DESC, entity_type ASC
            """, nativeQuery = true)
    List<Object[]> countByEntityType(@Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT admin_id, COUNT(*)
            FROM admin_audit_log
            WHERE created_at >= :from AND created_at < :to
            GROUP BY admin_id
            ORDER BY COUNT(*) DESC, admin_id ASC
            """, nativeQuery = true)
    List<Object[]> countByAdmin(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT COUNT(*)
            FROM admin_audit_log
            WHERE created_at >= :from AND created_at < :to
            """, nativeQuery = true)
    long countInWindow(@Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to);
}
