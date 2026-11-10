package org.example.helptreeservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One unique view of a post by one viewer on one calendar day. Rows are
 * inserted with {@code ON CONFLICT DO NOTHING}; the unique index is the source
 * of truth, so concurrent requests count at most once.
 */
@Entity
@Table(name = "post_views")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    /** {@code u:{userId}} for signed-in viewers, {@code h:{sha256}} otherwise. */
    @Column(name = "viewer_key", nullable = false, length = 80)
    private String viewerKey;

    @Column(name = "view_date", nullable = false)
    private LocalDate viewDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
