package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.helptreeservice.enums.ModerationActionType;
import org.example.helptreeservice.enums.ReportTargetType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Append-only record of an actual moderation decision (a sanction or a
 * content-visibility change). A single report may produce several actions.
 */
@Entity
@Table(name = "moderation_actions", indexes = {
    @Index(name = "idx_moderation_actions_target", columnList = "target_type, target_id, created_at"),
    @Index(name = "idx_moderation_actions_report", columnList = "report_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "report")
@ToString(exclude = "report")
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderator_id")
    private User moderator;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private ModerationActionType actionType;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

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
