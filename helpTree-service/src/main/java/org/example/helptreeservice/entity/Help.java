package org.example.helptreeservice.entity;

import org.example.helptreeservice.enums.HelpStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "helps", indexes = {
    @Index(name = "idx_helps_post_id", columnList = "post_id"),
    @Index(name = "idx_helps_helper_id", columnList = "helper_id"),
    @Index(name = "idx_helps_receiver_id", columnList = "receiver_id"),
    @Index(name = "idx_helps_status", columnList = "status"),
    @Index(name = "idx_helps_deleted", columnList = "deleted")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Help {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;              // Какой пост

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "helper_id", nullable = false)
    private User helper;            // Кто помогает

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;           // Кому помогают (автор поста)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HelpStatus status;       // Статус помощи

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;    // Когда откликнулся

    @Column(name = "completed_at")
    private LocalDateTime completedAt;    // Когда помог

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;     // Когда подтвердили

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Поля для soft-delete
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
