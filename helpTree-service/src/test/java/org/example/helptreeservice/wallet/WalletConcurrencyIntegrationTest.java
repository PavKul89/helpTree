package org.example.helptreeservice.wallet;

import org.example.helptreeservice.dto.posts.PostBoostResult;
import org.example.helptreeservice.dto.wallet.WalletOperationResult;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.enums.TransactionType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.repository.CoinTransactionRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.PostService;
import org.example.helptreeservice.service.WalletReconciliationService;
import org.example.helptreeservice.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-database proof of Block 5 wallet integrity: row-lock based credits,
 * idempotent debits, atomic boost + rollback, audit rows, database constraints
 * and read-only reconciliation. Run with {@code INTEGRATION_TEST=true}.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class WalletConcurrencyIntegrationTest {

    private static final int THREADS = 6;

    @Autowired private WalletService walletService;
    @Autowired private PostService postService;
    @Autowired private WalletReconciliationService reconciliationService;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private CoinTransactionRepository transactionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email LIKE 'wallet-it-%@test.local'", Long.class);
        if (ids.isEmpty()) {
            return;
        }

        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM admin_coin_audits WHERE user_id IN (" + in + ") "
                + "OR admin_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM coin_transactions WHERE user_id IN (" + in + ") "
                + "OR related_user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM posts WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (" + in + ")");
    }

    @Test
    void concurrentDailyBonusShouldCreditExactlyOnce() throws Exception {
        User user = createUser("Daily");

        List<Boolean> claims = runConcurrently(THREADS, () ->
                walletService.addDailyLoginBonus(user.getId()));

        assertEquals(1L, claims.stream().filter(Boolean::booleanValue).count());
        assertEquals(1L, reload(user).getHelpCoins());
        assertEquals(1L, countTransactions(user.getId(), "DAILY_LOGIN"));
    }

    @Test
    void concurrentSameSpendKeyShouldDebitExactlyOnce() throws Exception {
        User user = createUser("Spend");
        setBalance(user.getId(), 100L);

        List<WalletOperationResult> results = runConcurrently(THREADS, () ->
                walletService.purchaseVip(user.getId(), "same-vip-key"));

        assertEquals(1L, results.stream().filter(WalletOperationResult::getApplied).count());
        assertEquals(100L - 50L, reload(user).getHelpCoins());
        assertEquals(1L, countTransactions(user.getId(), "VIP"));
    }

    @Test
    void concurrentDifferentCreditsShouldNotLoseUpdates() throws Exception {
        User admin = createUser("Credit Admin");
        User user = createUser("Credits");
        setBalance(user.getId(), 0L);

        List<WalletOperationResult> results = runConcurrently(THREADS, () ->
                walletService.adminAddCoins(user.getId(), admin.getId(), 10L, "Concurrent credit",
                        "credit-" + UUID.randomUUID()));

        assertTrue(results.stream().allMatch(WalletOperationResult::getApplied));
        assertEquals((long) THREADS * 10L, reload(user).getHelpCoins());
        assertEquals(THREADS, countTransactions(user.getId(), "ADMIN_BONUS"));
        assertEquals(THREADS, countAudits(user.getId()));
    }

    @Test
    void concurrentSameAdminKeyShouldWriteOneAuditAndOneCredit() throws Exception {
        User admin = createUser("Admin");
        User user = createUser("Audited");
        setBalance(user.getId(), 0L);

        runConcurrently(THREADS, () ->
                walletService.adminAddCoins(user.getId(), admin.getId(), 25L,
                        "Single correction", "same-admin-key"));

        assertEquals(25L, reload(user).getHelpCoins());
        assertEquals(1L, countTransactions(user.getId(), "ADMIN_BONUS"));
        assertEquals(1L, countAudits(user.getId()));
    }

    @Test
    void concurrentSameBoostKeyShouldChargeAndExtendOnce() throws Exception {
        User user = createUser("Boost");
        setBalance(user.getId(), 100L);
        Post post = createPost(user);

        List<PostBoostResult> results = runConcurrently(THREADS, () ->
                postService.boostPost(post.getId(), user.getId(), false, "same-boost-key"));

        assertEquals(1L, results.stream().filter(PostBoostResult::applied).count());
        assertEquals(95L, reload(user).getHelpCoins());
        assertEquals(1L, countTransactions(user.getId(), "POST_BOOST"));
        assertNotNull(reloadPost(post).getBoostedUntil());
        assertTrue(reloadPost(post).getBoostedUntil().isAfter(LocalDateTime.now()));
    }

    @Test
    void failedBoostTransactionShouldRollBackTheDebit() {
        User user = createUser("Rollback");
        setBalance(user.getId(), 100L);
        Post post = createPost(user);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(status -> {
            walletService.chargeForPostBoost(user.getId(), post.getId(), "rollback-key");
            throw new IllegalStateException("Simulated boost failure");
        }));

        assertEquals(100L, reload(user).getHelpCoins());
        assertEquals(0L, countTransactions(user.getId(), "POST_BOOST"));
    }

    @Test
    void databaseShouldRejectNegativeBalanceAndDuplicateLedgerReference() {
        User user = createUser("Constraints");
        setBalance(user.getId(), 10L);

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("UPDATE users SET help_coins = -1 WHERE id = ?", user.getId()));

        walletService.addDailyLoginBonus(user.getId());

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("INSERT INTO coin_transactions "
                                + "(user_id, type, amount, description, reference_id, created_at) "
                                + "VALUES (?, 'DAILY_LOGIN', 1, 'duplicate', ?, CURRENT_TIMESTAMP)",
                        user.getId(),
                        "daily:" + java.time.LocalDate.now(java.time.ZoneId.of("Europe/Moscow"))));

        assertEquals(11L, reload(user).getHelpCoins());
    }

    @Test
    void reconciliationShouldDetectButNeverRepairDrift() {
        User user = createUser("Drift");
        jdbcTemplate.update("UPDATE users SET help_coins = 7 WHERE id = ?", user.getId());

        var anomalies = reconciliationService.findAnomalies();
        var anomaly = anomalies.stream()
                .filter(item -> user.getId().equals(item.getUserId()))
                .findFirst()
                .orElseThrow();

        assertEquals(7L, anomaly.getBalance());
        assertEquals(0L, anomaly.getLedgerTotal());
        assertEquals(7L, anomaly.getDifference());

        var page = reconciliationService.findAnomalies(PageRequest.of(0, 1));
        assertTrue(page.getTotalElements() >= 1);
        assertEquals(1, page.getSize());
        assertTrue(page.getContent().size() <= 1);

        reconciliationService.reconcile();

        assertEquals(7L, reload(user).getHelpCoins(),
                "Reconciliation must report drift without inventing or removing coins");
    }

    @Test
    void adminCorrectionShouldRespectLimitAndStoreWhoWhatWhy() {
        User admin = createUser("Admin Audit");
        User user = createUser("Audit Target");
        setBalance(user.getId(), 100L);

        assertThrows(BadRequestException.class, () ->
                walletService.adminAddCoins(user.getId(), admin.getId(), 1_000_001L,
                        "Too much", "too-much"));

        WalletOperationResult result = walletService.adminAddCoins(
                user.getId(), admin.getId(), -40L, "Remove mistaken bonus", "admin-audit-key");

        assertTrue(result.getApplied());
        assertEquals(60L, result.getBalance());
        assertEquals(1L, countAudits(user.getId()));
        Long adminId = jdbcTemplate.queryForObject(
                "SELECT admin_id FROM admin_coin_audits WHERE user_id = ?", Long.class, user.getId());
        assertEquals(admin.getId(), adminId);
    }

    // --------------------------------------------------------------- helpers

    private User createUser(String prefix) {
        User user = new User();
        user.setName(prefix + " User");
        user.setEmail("wallet-it-" + UUID.randomUUID() + "@test.local");
        user.setPassword("$2a$10$test-only-not-a-real-hash");
        user.setHelpCoins(0L);
        user.setDebtCount(0);
        user.setDeleted(false);
        return userRepository.saveAndFlush(user);
    }

    private Post createPost(User owner) {
        Post post = new Post();
        post.setTitle("Boost target");
        post.setDescription("Integration test post");
        post.setCategory("test");
        post.setAuthorName(owner.getName());
        post.setUser(owner);
        post.setStatus(PostStatus.OPEN);
        post.setDeleted(false);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        return postRepository.saveAndFlush(post);
    }

    private void setBalance(Long userId, long balance) {
        jdbcTemplate.update("UPDATE users SET help_coins = ? WHERE id = ?", balance, userId);
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private Post reloadPost(Post post) {
        return postRepository.findById(post.getId()).orElseThrow();
    }

    private long countTransactions(Long userId, String type) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coin_transactions WHERE user_id = ? AND type = ?",
                Long.class, userId, type);
        return count != null ? count : 0L;
    }

    private long countAudits(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_coin_audits WHERE user_id = ?", Long.class, userId);
        return count != null ? count : 0L;
    }

    private <T> List<T> runConcurrently(int threads, ThrowingSupplier<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return action.get();
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
