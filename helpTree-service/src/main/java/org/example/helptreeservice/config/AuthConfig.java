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

    /** Name of the HttpOnly cookie that carries the refresh token. */
    private String refreshCookieName = "helptree_refresh";
    /** Scoped to auth endpoints so the browser never sends it with ordinary API calls. */
    private String refreshCookiePath = "/api/auth";
    /** Reading a cookie value from JavaScript must never be possible for the refresh token. */
    private boolean refreshCookieSecure = true;
    /** SameSite=Strict blocks cross-site refresh even before the CSRF check. */
    private String refreshCookieSameSite = "Strict";
    /** Companion cookie, readable by the SPA, used for the CSRF double submit. */
    private String csrfCookieName = "helptree_csrf";
    private String csrfCookiePath = "/";
    private String csrfHeaderName = "X-CSRF-Token";
}
