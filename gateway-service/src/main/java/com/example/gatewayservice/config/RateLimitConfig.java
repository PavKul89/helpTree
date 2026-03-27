package com.example.gatewayservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitConfig {
    
    private boolean enabled = false;
    private int defaultLimit = 1000;
    private int authLimit = 100;
    private int createLimit = 50;
    private int windowSeconds = 60;
}
