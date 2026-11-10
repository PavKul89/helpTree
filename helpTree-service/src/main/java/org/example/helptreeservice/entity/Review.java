package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.example.helptreeservice.enums.ModerationStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews", indexes = {
    @Index(name = "idx_reviews_help_id", columnList = "help_id"),
    @Index(name = "idx_reviews_to_user_id", columnList = "to_user_id"),
    @Index(name = "idx_reviews_deleted", columnList = "deleted")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "help_id", nullable = false)
    private Help help;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** One reply from the review recipient. */
    @Column(name = "reply_text", columnDefinition = "TEXT")
    private String replyText;

    @Column(name = "reply_at")
    private LocalDateTime replyAt;

    /** Last time the reply was edited inside the 24-hour reply window. */
    @Column(name = "reply_updated_at")
    private LocalDateTime replyUpdatedAt;

    /** Last author edit inside the 24-hour edit window. */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "edit_count", nullable = false)
    private Integer editCount = 0;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Moderator-controlled visibility, independent from the author's delete. */
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    private ModerationStatus moderationStatus = ModerationStatus.ACTIVE;
}
