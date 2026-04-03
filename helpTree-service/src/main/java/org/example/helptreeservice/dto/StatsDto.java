package org.example.helptreeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsDto {
    private long totalUsers;
    private long totalHelps;
    private long activePosts;
}
