package org.example.ratingservice.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String city;
    private Integer helpedCount;
    private Integer debtCount;
    private Double rating;
    private String status;
    private String role;
    private LocalDateTime createdAt;
}
