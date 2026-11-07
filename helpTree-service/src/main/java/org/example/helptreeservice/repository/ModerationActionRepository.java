package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.ModerationAction;
import org.example.helptreeservice.enums.ModerationActionType;
import org.example.helptreeservice.enums.ReportTargetType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    @EntityGraph(attributePaths = {"moderator"})
    List<ModerationAction> findByReportIdOrderByCreatedAtAscIdAsc(Long reportId);

    @EntityGraph(attributePaths = {"moderator"})
    List<ModerationAction> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(
            ReportTargetType targetType, Long targetId);

    /**
     * Latest expired temporary hide per target. An older hide that has since
     * been superseded by a newer hide/removal must not restore the content.
     */
    @Query("""
            SELECT a FROM ModerationAction a
            WHERE a.actionType = :actionType
              AND a.expiresAt IS NOT NULL
              AND a.expiresAt <= :now
              AND NOT EXISTS (
                  SELECT 1 FROM ModerationAction newer
                  WHERE newer.targetType = a.targetType
                    AND newer.targetId = a.targetId
                    AND newer.id > a.id
              )
            ORDER BY a.createdAt ASC, a.id ASC
            """)
    List<ModerationAction> findExpiredHideActions(
            @Param("actionType") ModerationActionType actionType,
            @Param("now") LocalDateTime now);
}
