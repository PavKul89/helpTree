package org.example.helptreeservice.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WsTicketResponse {

    private String ticket;
    private int expiresInSeconds;
}
