package org.example.helptreeservice.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Optional filters for the global administrative audit trail. Every field is
 * nullable; an omitted field does not constrain the query.
 */
@Data
public class AdminAuditQuery {
    private Long adminId;
    private String action;
    private String entityType;
    private Long entityId;
    private LocalDateTime from;
    private LocalDateTime to;
    /** Free text over the reason, action and entity type. */
    private String q;
}
