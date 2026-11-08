package org.example.helptreeservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * One-time bootstrap administrator settings ({@code app.admin.*}).
 *
 * <p>The bootstrap is opt-in and disabled by default. Enabling it with a weak
 * password aborts startup instead of creating an account that would be a
 * security incident. The feature is not a "default admin": a fresh database
 * stays without an administrator until an operator explicitly asks for one.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    private boolean bootstrapEnabled = false;
    private String email;
    private String password;
}
