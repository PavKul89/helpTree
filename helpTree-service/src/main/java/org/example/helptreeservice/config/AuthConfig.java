package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthConfig {

    private int passwordResetTokenMinutes = 30;
    private int emailVerificationTokenHours = 24;
    private int emailChangeTokenHours = 24;
    private int sessionTouchMinutes = 1;
    private boolean trustForwardedHeaders = false;
    private String frontendBaseUrl = "http://localhost:3000";
}
