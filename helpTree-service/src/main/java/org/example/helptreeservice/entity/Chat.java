package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user1_id", "user2_id"})
}, indexes = {
    @Index(name = "idx_chats_user1_id", columnList = "user1_id"),
    @Index(name = "idx_chats_user2_id", columnList = "user2_id"),
    @Index(name = "idx_chats_last_message_at", columnList = "last_message_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
}
