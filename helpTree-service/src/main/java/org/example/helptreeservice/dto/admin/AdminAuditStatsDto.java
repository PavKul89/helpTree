package org.example.helptreeservice.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Aggregated view of administrative activity for a bounded time window.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditStatsDto {
    private long total;
    private LocalDateTime from;
    private LocalDateTime to;
    private Map<String, Long> byAction;
    private Map<String, Long> byEntityType;
    private Map<String, Long> byAdmin;
}
