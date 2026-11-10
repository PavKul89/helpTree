package org.example.helptreeservice.block22;

import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.repository.CoinTransactionRepository;
import org.example.helptreeservice.repository.CommentRepository;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.NotificationRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.ReportRepository;
import org.example.helptreeservice.repository.ReviewRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.AuthService;
import org.example.helptreeservice.service.AuthSessionService;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.ChatService;
import org.example.helptreeservice.service.CommentService;
import org.example.helptreeservice.service.HelpService;
import org.example.helptreeservice.service.JwtService;
import org.example.helptreeservice.service.PostService;
import org.example.helptreeservice.service.RatingService;
import org.example.helptreeservice.service.ReportService;
import org.example.helptreeservice.service.ReviewService;
import org.example.helptreeservice.service.UserBlockService;
import org.example.helptreeservice.service.UserService;
import org.example.helptreeservice.service.WalletReconciliationService;
import org.example.helptreeservice.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared fixtures for the Block 22 mandatory test suite.
 *
 * <p>Every integration test in this package uses the same disposable user
 * prefix ({@code b22-<uuid>@test.local}). The single {@link #cleanUp()} hook
 * removes every row the suite can create, in foreign-key order. No test wraps
 * itself in a transaction: the concurrency and after-commit scenarios require
 * real commits and parallel connections.</p>
 */
public abstract class Block22TestSupport {

    protected static final String EMAIL_PREFIX = "b22-";
    protected static final String EMAIL_SUFFIX = "@test.local";
    protected static final String IP = "203.0.113.22";
    protected static final String USER_AGENT = "Block22-JUnit";

    @Autowired protected UserRepository userRepository;
    @Autowired protected PostRepository postRepository;
    @Autowired protected HelpRepository helpRepository;
    @Autowired protected CommentRepository commentRepository;
    @Autowired protected ReviewRepository reviewRepository;
    @Autowired protected ReportRepository reportRepository;
    @Autowired protected MessageRepository messageRepository;
    @Autowired protected CoinTransactionRepository transactionRepository;
    @Autowired protected NotificationRepository notificationRepository;

    @Autowired protected UserService userService;
    @Autowired protected PostService postService;
    @Autowired protected HelpService helpService;
    @Autowired protected WalletService walletService;
    @Autowired protected WalletReconciliationService walletReconciliationService;
    @Autowired protected ChatService chatService;
    @Autowired protected ReviewService reviewService;
    @Autowired protected CommentService commentService;
    @Autowired protected ReportService reportService;
    @Autowired protected RatingService ratingService;
    @Autowired protected UserBlockService userBlockService;
    @Autowired protected AuthService authService;
    @Autowired protected AuthSessionService authSessionService;
    @Autowired protected JwtService jwtService;
    @Autowired protected AuthorizationService authorizationService;

    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUpBlock22Data() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%" + EMAIL_SUFFIX);
        if (ids.isEmpty()) {
            return;
        }
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));

        // Reports and their children reference users and each other.
        jdbcTemplate.update("DELETE FROM admin_audit_log WHERE admin_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM moderation_actions WHERE moderator_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM report_events WHERE actor_id IN (" + in + ") "
                + "OR report_id IN (SELECT id FROM reports WHERE reporter_id IN (" + in + "))");
        jdbcTemplate.update("DELETE FROM reports WHERE reporter_id IN (" + in + ") "
                + "OR assigned_moderator_id IN (" + in + ") OR resolved_by_id IN (" + in + ")");

        jdbcTemplate.update("DELETE FROM notifications WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM notification_preferences WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM account_notification_preferences WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM account_tokens WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM outbox_events WHERE aggregate_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM login_locks WHERE lock_key LIKE ?", "b22:%");

        // Chats own messages and chat_user_state (ON DELETE CASCADE).
        jdbcTemplate.update("DELETE FROM chats WHERE user1_id IN (" + in + ") OR user2_id IN (" + in + ")");

        jdbcTemplate.update("DELETE FROM help_status_history WHERE help_id IN "
                + "(SELECT id FROM helps WHERE helper_id IN (" + in + ") OR receiver_id IN (" + in + "))");
        jdbcTemplate.update("DELETE FROM reviews WHERE from_user_id IN (" + in + ") "
                + "OR to_user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM helps WHERE helper_id IN (" + in + ") "
                + "OR receiver_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM comments WHERE user_id IN (" + in + ") "
                + "OR deleted_by_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM post_views WHERE post_id IN "
                + "(SELECT id FROM posts WHERE user_id IN (" + in + "))");
        jdbcTemplate.update("DELETE FROM posts WHERE user_id IN (" + in + ") OR helper_id IN (" + in + ")");

        jdbcTemplate.update("DELETE FROM coin_transactions WHERE user_id IN (" + in + ") "
                + "OR related_user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM admin_coin_audits WHERE user_id IN (" + in + ") "
                + "OR admin_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM paid_feature_periods WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM rating_history WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM user_rating_stats WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM achievements WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM auth_refresh_tokens WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM auth_sessions WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM user_privacy_settings WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM user_favorites WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM user_blocks_relations WHERE blocker_id IN (" + in + ") "
                + "OR blocked_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM user_blocks WHERE user_id IN (" + in + ") "
                + "OR blocked_by_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM image_objects WHERE owner_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (" + in + ")");
    }

    // ------------------------------------------------------------------ users

    protected User createUser(String name) {
        return createUser(name, Role.USER);
    }

    protected User createAdmin(String name) {
        return createUser(name, Role.ADMIN);
    }

    protected User createUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(EMAIL_PREFIX + UUID.randomUUID() + EMAIL_SUFFIX);
        user.setPassword("$2a$10$test-only-not-a-real-hash");
        user.setRole(role);
        user.setEmailVerified(true);
        user.setHelpCoins(0L);
        user.setDebtCount(0);
        user.setHelpedCount(0);
        user.setDeleted(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.saveAndFlush(user);
    }

    protected User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    protected void setBalance(Long userId, long balance) {
        jdbcTemplate.update("UPDATE users SET help_coins = ? WHERE id = ?", balance, userId);
    }

    protected void setDebt(User user, int debtCount, int helpedCount) {
        jdbcTemplate.update("UPDATE users SET debt_count = ?, helped_count = ? WHERE id = ?",
                debtCount, helpedCount, user.getId());
    }

    protected void markDeleted(Long userId) {
        jdbcTemplate.update("UPDATE users SET deleted = TRUE, deleted_at = ? WHERE id = ?",
                LocalDateTime.now(), userId);
    }

    protected void blockAccount(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("UPDATE users SET blocked_at = ?, blocked_until = ? WHERE id = ?",
                now, now.plusDays(1), userId);
    }

    protected AuthSessionService.IssuedTokens issueSession(User user) {
        return authSessionService.createSession(reload(user), IP, USER_AGENT);
    }

    // ------------------------------------------------------------------ posts

    protected Post createPost(User author) {
        Post post = new Post();
        post.setTitle("Block 22 post " + UUID.randomUUID());
        post.setDescription("Integration test post for the mandatory test suite");
        post.setCategory("OTHER");
        post.setAuthorName(author.getName());
        post.setUser(author);
        post.setStatus(PostStatus.OPEN);
        post.setDeleted(false);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        return postRepository.saveAndFlush(post);
    }

    protected Post reloadPost(Post post) {
        return postRepository.findById(post.getId()).orElseThrow();
    }

    protected HelpRequest helpRequest(Post post) {
        HelpRequest request = new HelpRequest();
        request.setPostId(post.getId());
        return request;
    }

    protected HelpResponse accept(User helper, Post post) {
        return helpService.acceptHelp(helpRequest(post), helper.getId());
    }

    /**
     * Drives a fresh post through ACCEPTED -> COMPLETED -> CONFIRMED.
     */
    protected HelpResponse createConfirmedHelp(User helper, User receiver) {
        Post post = createPost(receiver);
        HelpResponse accepted = accept(helper, post);
        helpService.completeHelp(accepted.getId(), helper.getId(), false);
        return helpService.confirmHelp(accepted.getId(), receiver.getId(), false);
    }

    // -------------------------------------------------------------- counters

    protected long countTransactions(Long userId, String type) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coin_transactions WHERE user_id = ? AND type = ?",
                Long.class, userId, type);
        return count != null ? count : 0L;
    }

    protected long countTransactions(Long userId, String type, Long postId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coin_transactions "
                        + "WHERE user_id = ? AND type = ? AND related_post_id = ?",
                Long.class, userId, type, postId);
        return count != null ? count : 0L;
    }

    protected long ledgerTotal(Long userId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM coin_transactions WHERE user_id = ?",
                Long.class, userId);
        return total != null ? total : 0L;
    }

    protected long countNotifications(Long userId, String type) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = ?",
                Long.class, userId, type);
        return count != null ? count : 0L;
    }

    // ------------------------------------------------------------ concurrency

    protected <T> List<T> runConcurrently(int attempts, ThrowingSupplier<T> action) throws Exception {
        List<Object> results = runConcurrentlyAllowFailure(attempts, index -> action.get());
        List<T> successes = new ArrayList<>();
        for (Object result : results) {
            if (result instanceof RuntimeException runtime) {
                throw runtime;
            }
            @SuppressWarnings("unchecked")
            T value = (T) result;
            successes.add(value);
        }
        return successes;
    }

    /**
     * Runs {@code action} on {@code attempts} threads that are released at the
     * same time. Runtime exceptions are returned as values so a race loser can be
     * asserted as a domain error instead of failing the test.
     */
    protected List<Object> runConcurrentlyAllowFailure(int attempts, IndexedThrowingSupplier action)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < attempts; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return action.get(index);
                    } catch (RuntimeException e) {
                        return e;
                    }
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Workers did not become ready");
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get();
    }

    @FunctionalInterface
    protected interface IndexedThrowingSupplier {
        Object get(int index);
    }
}
