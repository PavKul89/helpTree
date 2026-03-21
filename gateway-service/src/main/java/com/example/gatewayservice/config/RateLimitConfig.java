package com.example.gatewayservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitConfig {
    
    private boolean enabled = true;
    private int defaultLimit = 60;
    private int authLimit = 10;
    private int createLimit = 5;
    private int windowSeconds = 60;
}
