package org.example.helptreeservice.entity;

import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email"),
    @Index(name = "idx_users_deleted", columnList = "deleted")
})
@BatchSize(size = 50)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Email
    @Size(max = 200)
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false)
    private String password;

    @Size(max = 30)
    private String phone;

    @Size(max = 100)
    private String city;

    private Double latitude;
    private Double longitude;

    @Column(name = "helped_count")
    private Integer helpedCount = 0;

    @Column(name = "debt_count", nullable = false)
    private Integer debtCount = 0;

    @Column(nullable = false)
    private Double rating = 3.0;

    @Column(name = "birth_date")
    private LocalDateTime birthDate;

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.NEWBIE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Terminal marker for the irreversible anonymization flow. Once set, the row
     * no longer contains personal data, so it must never be restored as a normal
     * account.
     */
    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_favorites", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "post_id", nullable = false)
    private List<Long> favoritePostIds = new ArrayList<>();

    @Column(name = "telegram_chat_id", length = 50)
    private String telegramChatId;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * Last authenticated activity. Touched by access-token validation at most
     * once per {@code app.auth.last-seen-touch-minutes}; never written on every
     * request. Public profiles derive "online" from this value only when the
     * owner enabled {@code showOnlineStatus}.
     */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "token_version", nullable = false)
    private Long tokenVersion = 0L;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    @Column(name = "debt_started_at")
    private LocalDateTime debtStartedAt;

    @Size(max = 500)
    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "help_coins")
    private Long helpCoins = 0L;

    @Column(name = "vip_until")
    private LocalDateTime vipUntil;

    @Size(max = 20)
    @Column(name = "nickname_color")
    private String nicknameColor;

    public boolean isVip() {
        return vipUntil != null && vipUntil.isAfter(LocalDateTime.now());
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        normalizeEmail();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        normalizeEmail();
    }

    /**
     * Email is a login identity. Normalizing it at the persistence boundary
     * guarantees that every write path (registration, admin invite, email
     * change, anonymization) stores the same representation, so the unique
     * index on lower(email) behaves consistently.
     */
    private void normalizeEmail() {
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
