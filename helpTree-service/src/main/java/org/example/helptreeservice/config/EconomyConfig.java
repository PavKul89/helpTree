package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.economy")
public class EconomyConfig {

    private long coinsPerHelp = 10L;
    private long coinsPerHelpReceived = 2L;
    private long coinsPerReview = 2L;
    private long coinsPerDailyLogin = 1L;
    private long firstHelpBonus = 3L;
    private long boostPrice = 5L;
    private int boostHours = 24;
    private int refreshTokenDays = 7;
    private int debtBlockThreshold = 5;
    private int debtWarningThreshold = 3;
    private int debtCreatePostThreshold = 2;
}
