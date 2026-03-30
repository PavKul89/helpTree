package org.example.helptreeservice.dto.users;

import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String city;
    private Double latitude;
    private Double longitude;
    private Integer helpedCount;
    private Integer debtCount;
    private Double rating;
    private Role role;
    private UserStatus status;
    private LocalDateTime createdAt;
    private String avatarUrl;
    private LocalDateTime birthDate;
    private LocalDateTime blockedAt;
    private LocalDateTime debtStartedAt;
}
