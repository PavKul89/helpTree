package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.helptreeservice.enums.ReportPriority;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports", indexes = {
    @Index(name = "idx_reports_status_created", columnList = "status, created_at"),
    @Index(name = "idx_reports_target", columnList = "target_type, target_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "duplicateOf")
@ToString(exclude = "duplicateOf")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportReason reason;

    /** Public explanation written by the reporter. */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportPriority priority = ReportPriority.NORMAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_moderator_id")
    private User assignedModerator;

    @Column(length = 1000)
    private String resolution;

    /** Staff-only note. Never exposed through user DTOs. */
    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    /** Set when this report is declared a duplicate of an earlier report. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private Report duplicateOf;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
