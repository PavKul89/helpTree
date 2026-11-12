package org.example.helptreeservice.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server-side price and duration catalog. The frontend must render these values
 * instead of keeping its own copy of economy constants.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletPricingDto {
    private Long boostPrice;
    private Integer boostHours;
    private Long vipPrice;
    private Integer vipDays;
    private Long nicknameColorPrice;
    private Long accountUnblockPrice;
    private Integer accountUnblockDebtReduction;
    /** Debt above this threshold blocks the account again, so it limits unblock eligibility. */
    private Integer debtBlockThreshold;
    private Long dailyBonusCoins;

    /* Earning rates: the UI renders these instead of hardcoding economy constants. */
    private Long coinsPerHelp;
    private Long coinsPerHelpReceived;
    private Long coinsPerReview;
    private Long firstHelpBonus;
}
