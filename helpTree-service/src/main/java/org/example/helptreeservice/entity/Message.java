package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.helptreeservice.converter.StringListJsonConverter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_messages_chat_id", columnList = "chat_id"),
    @Index(name = "idx_messages_sender_id", columnList = "sender_id"),
    @Index(name = "idx_messages_is_read", columnList = "is_read")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_id")
    private User deletedBy;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "edit_count", nullable = false)
    @Builder.Default
    private Integer editCount = 0;

    /** Up to three image URLs owned by the sender. Stored as a JSON array in TEXT. */
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "attachment_urls", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private List<String> attachmentUrls = new ArrayList<>();

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
