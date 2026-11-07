package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.helptreeservice.enums.ModerationStatus;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments", indexes = {
    @Index(name = "idx_comments_post_id", columnList = "post_id"),
    @Index(name = "idx_comments_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    /** 0 for a root comment; computed from the parent when a reply is stored. */
    @Builder.Default
    @Column(nullable = false)
    private Integer depth = 0;

    /**
     * Comments are never physically deleted: replies reference them and the
     * tree must survive moderation. The row keeps the original content for
     * audit, while the API masks it when {@code deleted} is true.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_id")
    private User deletedBy;

    /**
     * True when the author removed the comment, false when moderation removed
     * it. Kept as an explicit column so the tombstone text does not depend on
     * mutable user data.
     */
    @Builder.Default
    @Column(name = "deleted_by_author", nullable = false)
    private Boolean deletedByAuthor = false;

    @Column(name = "deleted_reason", length = 40)
    private String deletedReason;

    /** Moderator-controlled visibility, independent from the author's delete. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    private ModerationStatus moderationStatus = ModerationStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
