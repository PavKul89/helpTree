package org.example.helptreeservice.dto.users;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicDto {
    private Long id;
    private String name;
    private Double rating;
    private Integer helpedCount;
    private Integer debtCount;
    private String avatarUrl;
    private String city;
    /** Present only when the owner enabled {@code showPhone}. */
    private String phone;
    /** Derived from last activity; null when the owner hides online status. */
    private Boolean online;
    private String nicknameColor;
}
