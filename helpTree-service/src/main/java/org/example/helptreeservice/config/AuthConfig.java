package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthConfig {

    private int passwordResetTokenMinutes = 30;
    private int accountInvitationHours = 72;
    private int emailVerificationTokenHours = 24;
    private int emailChangeTokenHours = 24;
    private int sessionTouchMinutes = 1;
    /**
     * Authenticated activity is persisted at most once per this many minutes.
     * Without throttling every API request would write the users row.
     */
    private int lastSeenTouchMinutes = 1;
    /** A user is shown as online when last seen within this many minutes. */
    private int onlineWindowMinutes = 5;
    private boolean trustForwardedHeaders = false;
    private String frontendBaseUrl = "http://localhost:3000";
    /**
     * Lifetime of the one-purpose ticket used only for the WebSocket handshake.
     * The full access token is still required in the STOMP CONNECT frame.
     */
    private int wsTicketSeconds = 60;
}
