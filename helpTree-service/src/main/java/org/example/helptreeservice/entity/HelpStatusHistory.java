package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.HelpActorRole;
import org.example.helptreeservice.enums.HelpStatus;

import java.time.LocalDateTime;

/**
 * Append-only audit trail for help status transitions. Rows are never updated or
 * deleted by lifecycle operations, so the full history of a help fact remains
 * available to its participants and administrators.
 */
@Entity
@Table(name = "help_status_history", indexes = {
    @Index(name = "idx_help_status_history_help_id", columnList = "help_id, created_at"),
    @Index(name = "idx_help_status_history_actor", columnList = "actor_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HelpStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "help_id", nullable = false)
    private Help help;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private HelpStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private HelpStatus toStatus;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 20)
    private HelpActorRole actorRole;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
