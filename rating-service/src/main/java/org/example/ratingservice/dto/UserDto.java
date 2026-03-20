package org.example.ratingservice.dto;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String name;
    private Double rating;
    private Integer helpedCount;
    private Integer debtCount;
}
