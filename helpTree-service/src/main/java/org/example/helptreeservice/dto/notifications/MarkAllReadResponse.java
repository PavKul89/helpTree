package org.example.helptreeservice.dto.notifications;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkAllReadResponse {

    private int updated;
    private long unreadCount;
}
