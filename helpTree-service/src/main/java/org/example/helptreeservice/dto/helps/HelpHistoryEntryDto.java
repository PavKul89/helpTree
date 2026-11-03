package org.example.helptreeservice.dto.helps;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HelpHistoryEntryDto {
    private Long id;
    private String fromStatus;
    private String toStatus;
    private Long actorId;
    private String actorRole;
    private String reason;
    private LocalDateTime createdAt;
}
