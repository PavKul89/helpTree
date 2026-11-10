package org.example.helptreeservice.block22;

import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.dto.posts.PostBoostResult;
import org.example.helptreeservice.dto.wallet.WalletOperationResult;
import org.example.helptreeservice.dto.wallet.WalletPricingDto;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 22.3 - wallet integrity: server-side pricing, atomic credits/debits, one
 * reward per business action, boost rollback and ledger reconciliation.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class Block22WalletIntegrationTest extends Block22TestSupport {

    @Autowired private EconomyConfig economyConfig;

    @Test
    void vipIsPricedByTheServerAndNotByTheCaller() {
        User user = createUser("VIP buyer");
        setBalance(user.getId(), 1000L);

        WalletPricingDto pricing = walletService.getPricing();
        assertEquals(economyConfig.getVipPrice(), pricing.getVipPrice());
        assertEquals(economyConfig.getVipDays(), pricing.getVipDays());

        WalletOperationResult purchased = walletService.purchaseVip(user.getId(), "block22-vip-price");
        assertTrue(purchased.getApplied());
        assertEquals(economyConfig.getVipPrice(), purchased.getPrice(),
                "The debit must be the server price, not a client supplied amount");
        assertEquals(1000L - economyConfig.getVipPrice(), purchased.getBalance());
        assertTrue(reload(user).isVip());
        assertEquals(1, countTransactions(user.getId(), "VIP"));

        WalletOperationResult replay = walletService.purchaseVip(user.getId(), "block22-vip-price");
        assertFalse(replay.getApplied(), "The same idempotency key must not charge twice");
        assertEquals(1, countTransactions(user.getId(), "VIP"));
        assertEquals(1000L - economyConfig.getVipPrice(), reload(user).getHelpCoins());
    }

    @Test
    void concurrentCreditsAndDebitsLoseNothingAndNeverGoNegative() throws Exception {
        User user = createUser("Concurrent wallet");
        // Pre-fund through the ledger so the final balance and ledger must agree.
        for (int i = 0; i < 10; i++) {
            walletService.adminAddCoins(user.getId(), user.getId(), 10L,
                    "Seed credit " + i, "block22-seed-" + i);
        }
        assertEquals(100L, reload(user).getHelpCoins());

        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            posts.add(createPost(user));
        }

        int operations = 12;
        List<Object> results = runConcurrentlyAllowFailure(operations, index -> {
            if (index % 2 == 0) {
                return walletService.adminAddCoins(user.getId(), user.getId(), 10L,
                        "Concurrent credit " + index, "block22-credit-" + index);
            }
            Post post = posts.get(index / 2);
            return walletService.chargeForPostBoost(user.getId(), post.getId(),
                    "block22-debit-" + index);
        });

        assertTrue(results.stream().allMatch(result -> result instanceof WalletOperationResult),
                "No concurrent credit or debit may fail");
        assertTrue(results.stream().map(WalletOperationResult.class::cast)
                .allMatch(WalletOperationResult::getApplied));

        long expected = 100L + 6 * 10L - 6 * economyConfig.getBoostPrice();
        User reloaded = reload(user);
        assertEquals(expected, reloaded.getHelpCoins());
        assertTrue(reloaded.getHelpCoins() >= 0, "The balance must never go negative");
        assertEquals(reloaded.getHelpCoins(), ledgerTotal(user.getId()),
                "The ledger must equal the actual balance after every operation");

        boolean anomaly = walletReconciliationService.findAnomalies().stream()
                .anyMatch(item -> user.getId().equals(item.getUserId()));
        assertFalse(anomaly, "Reconciliation must not find drift for a correctly audited wallet");
    }

    @Test
    void insufficientFundsCannotDriveTheBalanceNegative() {
        User user = createUser("Poor owner");
        setBalance(user.getId(), 1L);
        Post post = createPost(user);
        long price = economyConfig.getBoostPrice();
        assertTrue(price > 1);

        assertThrows(BadRequestException.class,
                () -> postService.boostPost(post.getId(), user.getId(), false, "block22-insufficient"));

        assertEquals(1L, reload(user).getHelpCoins());
        assertEquals(0, countTransactions(user.getId(), "POST_BOOST", post.getId()));
        assertEquals(0L, ledgerTotal(user.getId()));
    }

    @Test
    void aRewardIsGrantedOncePerAction() {
        User helper = createUser("Reward helper");
        User receiver = createUser("Reward receiver");
        Post post = createPost(receiver);

        walletService.addCoinsForHelp(helper.getId(), receiver.getId(), post.getId());
        walletService.addCoinsForHelp(helper.getId(), receiver.getId(), post.getId());
        assertEquals(1, countTransactions(helper.getId(), "HELP_GIVEN", post.getId()),
                "A repeated help reward must be a no-op");

        walletService.addCoinsForReceivedHelp(receiver.getId(), helper.getId(), post.getId());
        walletService.addCoinsForReceivedHelp(receiver.getId(), helper.getId(), post.getId());
        assertEquals(1, countTransactions(receiver.getId(), "HELP_RECEIVED", post.getId()));

        long reviewId = 987654L;
        walletService.addCoinsForReview(helper.getId(), reviewId);
        walletService.addCoinsForReview(helper.getId(), reviewId);
        assertEquals(1, countTransactions(helper.getId(), "REVIEW_BONUS"));

        assertTrue(walletService.addDailyLoginBonus(helper.getId()));
        assertFalse(walletService.addDailyLoginBonus(helper.getId()));
        assertEquals(1, countTransactions(helper.getId(), "DAILY_LOGIN"));
    }

    @Test
    void boostIsRefusedWithoutDebitWhenThePostIsNoLongerOpen() {
        User user = createUser("Boost owner");
        setBalance(user.getId(), 100L);
        Post post = createPost(user);
        jdbcTemplate.update("UPDATE posts SET status = 'CANCELLED' WHERE id = ?", post.getId());

        assertThrows(BadRequestException.class,
                () -> postService.boostPost(post.getId(), user.getId(), false, "block22-closed-boost"));

        assertEquals(100L, reload(user).getHelpCoins(), "A rejected boost must not be charged");
        assertEquals(0, countTransactions(user.getId(), "POST_BOOST", post.getId()));
        assertEquals(0L, ledgerTotal(user.getId()));
    }

    @Test
    void aFailedBoostTransactionRollsBackTheDebit() {
        User user = createUser("Boost rollback");
        setBalance(user.getId(), 100L);
        Post post = createPost(user);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(status -> {
            walletService.chargeForPostBoost(user.getId(), post.getId(), "block22-rollback");
            throw new IllegalStateException("Simulated failure after the debit");
        }));

        assertEquals(100L, reload(user).getHelpCoins());
        assertEquals(0, countTransactions(user.getId(), "POST_BOOST", post.getId()));
    }

    @Test
    void aSuccessfulBoostDebitsOnceAndMovesThePostClock() {
        User user = createUser("Boost success");
        setBalance(user.getId(), 100L);
        Post post = createPost(user);

        PostBoostResult first = postService.boostPost(post.getId(), user.getId(), false, "block22-boost-once");
        assertTrue(first.applied());
        assertEquals(100L - economyConfig.getBoostPrice(), first.balance());
        assertNotNull(reloadPost(post).getBoostedUntil());
        assertTrue(reloadPost(post).getBoostedUntil().isAfter(java.time.LocalDateTime.now()));

        PostBoostResult replay = postService.boostPost(post.getId(), user.getId(), false, "block22-boost-once");
        assertFalse(replay.applied(), "Retrying the same boost key must be idempotent");
        assertEquals(1, countTransactions(user.getId(), "POST_BOOST", post.getId()));
        assertEquals(100L - economyConfig.getBoostPrice(), reload(user).getHelpCoins());
    }
}
