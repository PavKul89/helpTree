package com.example.helpTree.entity;

import com.example.helpTree.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    private String city;

    @Column(name = "helped_count")
    private Integer helpedCount = 0;

    @Column(name = "debt_count")
    private Integer debtCount = 0;

    private Double rating = 0.0;

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.NEWBIE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
