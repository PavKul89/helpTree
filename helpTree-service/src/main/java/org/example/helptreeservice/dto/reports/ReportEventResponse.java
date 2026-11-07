package org.example.helptreeservice.dto.reports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.ReportEventType;
import org.example.helptreeservice.enums.ReportStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * History entry. For user-facing responses {@code internalNote} is always null;
 * administrators receive the staff note.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportEventResponse {

    private Long id;
    private Long actorId;
    private String actorName;
    private ReportEventType eventType;
    private ReportStatus fromStatus;
    private ReportStatus toStatus;
    private String internalNote;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
