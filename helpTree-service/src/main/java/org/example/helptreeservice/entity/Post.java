package org.example.helptreeservice.entity;

import org.example.helptreeservice.enums.ModerationStatus;
import org.example.helptreeservice.enums.PostStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.BatchSize;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_posts_user_id", columnList = "user_id"),
    @Index(name = "idx_posts_status", columnList = "status"),
    @Index(name = "idx_posts_deleted", columnList = "deleted"),
    @Index(name = "idx_posts_created_at", columnList = "created_at")
})
@BatchSize(size = 50)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private String category;
    private String authorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "helper_id")
    private User helper;

    @Enumerated(EnumType.STRING)
    private PostStatus status;

    @ElementCollection
    @CollectionTable(name = "post_images", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "image_url", columnDefinition = "TEXT")
    @BatchSize(size = 50)
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Moderator-controlled visibility. It is independent from {@code deleted}:
     * the author's deletion and a moderation decision must stay distinguishable.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    private ModerationStatus moderationStatus = ModerationStatus.ACTIVE;

    private Double latitude;
    private Double longitude;

    @Column(name = "boosted_until")
    private LocalDateTime boostedUntil;

    /**
     * Projection of non-deleted comments. It is maintained atomically in the
     * same transaction as comment create/delete, so post lists do not need a
     * correlated count subquery.
     */
    @Column(name = "comments_count", nullable = false)
    private Integer commentsCount = 0;

    /** Unique views per viewer per day; maintained by post_views. */
    @Column(name = "views_count", nullable = false)
    private Integer viewsCount = 0;

    /**
     * Number of live help responses for this post; recomputed from helps after
     * every accept/cancel transition.
     */
    @Column(name = "responses_count", nullable = false)
    private Integer responsesCount = 0;

    /**
     * SHA-256 of the normalised title + description. Together with the author
     * and created_at it backs the 24-hour duplicate guard.
     */
    @Column(name = "content_fingerprint", length = 64)
    private String contentFingerprint;

    public boolean isBoosted() {
        return boostedUntil != null && boostedUntil.isAfter(LocalDateTime.now());
    }
}