package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

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
    private String boostCleanupCron = "0 15 * * * *";

    private long vipPrice = 50L;
    private int vipDays = 30;

    private long nicknameColorPrice = 20L;

    private long accountUnblockPrice = 100L;
    private int accountUnblockDebtReduction = 5;

    private ZoneId dailyBonusZone = ZoneId.of("Europe/Moscow");

    private long adminMaxCoinsPerOperation = 1_000_000L;

    private int idempotencyKeyMaxLength = 100;

    private boolean reconciliationEnabled = true;
    private String reconciliationCron = "0 0 4 * * *";

    private int refreshTokenDays = 7;
    private int debtBlockThreshold = 5;
    private int debtWarningThreshold = 3;
    private int debtCreatePostThreshold = 2;
    private int debtPerReceivedHelp = 2;
    private int debtClearThreshold = 2;
    private int debtBlockDays = 7;
}
