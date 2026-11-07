package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.DigestFrequency;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Account-wide notification settings from Block 14 (legacy
 * {@code /api/account/notifications}). Block 17 renamed the table to
 * {@code account_notification_preferences} to free the name for the
 * per-type/per-channel model; the API and behaviour are unchanged.
 */
@Entity
@Table(name = "account_notification_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountNotificationPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled;

    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled;

    @Column(name = "in_app_enabled", nullable = false)
    private Boolean inAppEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "digest_frequency", nullable = false, length = 20)
    private DigestFrequency digestFrequency;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

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
