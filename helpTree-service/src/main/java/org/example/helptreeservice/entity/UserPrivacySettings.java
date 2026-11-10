package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ProfileVisibility;

import java.time.LocalDateTime;

/**
 * Per-user profile visibility. Absent rows use the documented defaults, which
 * match the pre-V50 behaviour (registered members only, city visible).
 */
@Entity
@Table(name = "user_privacy_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrivacySettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_visibility", nullable = false, length = 20)
    private ProfileVisibility profileVisibility;

    @Column(name = "show_city", nullable = false)
    private Boolean showCity;

    @Column(name = "show_email", nullable = false)
    private Boolean showEmail;

    @Column(name = "show_phone", nullable = false)
    private Boolean showPhone;

    /** Whether the public profile may show the derived online indicator. */
    @Column(name = "show_online_status", nullable = false)
    private Boolean showOnlineStatus;

    @Column(name = "allow_direct_messages", nullable = false)
    private Boolean allowDirectMessages;

    @Column(name = "searchable", nullable = false)
    private Boolean searchable;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
