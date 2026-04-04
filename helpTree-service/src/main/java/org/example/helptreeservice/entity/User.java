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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email"),
    @Index(name = "idx_users_deleted", columnList = "deleted")
})
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

    @Column(name = "debt_count")
    private Integer debtCount = 0;

    private Double rating = 0.0;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_favorites", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "post_id", nullable = false)
    private List<Long> favoritePostIds = new ArrayList<>();

    @Column(name = "telegram_chat_id", length = 50)
    private String telegramChatId;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "debt_started_at")
    private LocalDateTime debtStartedAt;

    @Size(max = 500)
    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "help_coins")
    private Long helpCoins = 0L;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
