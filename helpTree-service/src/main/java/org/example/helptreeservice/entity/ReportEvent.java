package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.helptreeservice.enums.ReportEventType;
import org.example.helptreeservice.enums.ReportStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Append-only history of a report. Every status change, assignment, note and
 * applied action creates one row, so a decision can never be changed silently.
 */
@Entity
@Table(name = "report_events", indexes = {
    @Index(name = "idx_report_events_report_created", columnList = "report_id, created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "report")
@ToString(exclude = "report")
public class ReportEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private ReportEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ReportStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20)
    private ReportStatus toStatus;

    /** Staff-only explanation. Never exposed through a user DTO. */
    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (metadata == null) {
            metadata = new HashMap<>();
        }
    }
}
