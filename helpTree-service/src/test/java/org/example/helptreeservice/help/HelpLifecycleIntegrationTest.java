package org.example.helptreeservice.help;

import org.example.helptreeservice.dto.helps.HelpHistoryEntryDto;
import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.HelpStatusHistory;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserRatingStats;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.HelpStatusHistoryRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.HelpService;
import org.example.helptreeservice.service.HelpSlaScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * Real-database proof of the Block 4 help lifecycle guarantees:
 * role enforcement, the ACCEPTED -> COMPLETED -> CONFIRMED state machine,
 * single successful-help award point, idempotent retries and race safety.
 *
 * <p>The test commits data (required for real concurrency) and removes only rows
 * belonging to its own uniquely named users afterwards. Run with
 * {@code INTEGRATION_TEST=true}.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class HelpLifecycleIntegrationTest {

    private static final int CONCURRENT_ACCEPTS = 6;
    private static final int CONCURRENT_CONFIRMS = 4;

    @Autowired private HelpService helpService;
    @Autowired private HelpSlaScheduler slaScheduler;
    @Autowired private HelpRepository helpRepository;
    @Autowired private HelpStatusHistoryRepository historyRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRatingStatsRepository statsRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email LIKE 'help-it-%@test.local'", Long.class);
        if (ids.isEmpty()) {
            return;
        }

        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM reviews WHERE help_id IN "
                + "(SELECT id FROM helps WHERE helper_id IN (" + in + ") OR receiver_id IN (" + in + "))");
        jdbcTemplate.update("DELETE FROM help_status_history WHERE help_id IN "
                + "(SELECT id FROM helps WHERE helper_id IN (" + in + ") OR receiver_id IN (" + in + "))");
        jdbcTemplate.update("DELETE FROM helps WHERE helper_id IN (" + in + ") OR receiver_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM posts WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM coin_transactions WHERE user_id IN (" + in + ") "
                + "OR related_user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM rating_history WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM user_rating_stats WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM achievements WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (" + in + ")");
    }

    @Test
    void fullLifecycleShouldFinalizeExactlyOnceAndIgnoreRetries() {
        User helper = createUser("Lifecycle Helper");
        User receiver = createUser("Lifecycle Receiver");
        Post post = createPost(receiver);

        HelpResponse accepted = helpService.acceptHelp(acceptRequest(post), helper.getId());
        assertEquals("ACCEPTED", accepted.getStatus());
        assertEquals(PostStatus.IN_PROGRESS, reloadPost(post).getStatus());
        assertEquals(0, countTransactions(helper.getId(), "HELP_GIVEN", post.getId()),
                "Accepting a help must not award coins");

        // Only the helper may complete.
        assertThrows(ForbiddenException.class,
                () -> helpService.completeHelp(accepted.getId(), receiver.getId(), false));
        assertEquals(HelpStatus.ACCEPTED, reloadHelp(accepted).getStatus());

        HelpResponse completed = helpService.completeHelp(accepted.getId(), helper.getId(), false);
        assertEquals("COMPLETED", completed.getStatus());
        assertEquals(0, countTransactions(helper.getId(), "HELP_GIVEN", post.getId()),
                "Completing a help must not award coins yet");
        UserRatingStats afterComplete = statsRepository.findByUserId(helper.getId()).orElseThrow();
        assertEquals(1L, afterComplete.getActiveHelpsGiven(),
                "An accepted/completed help is an active projection record");
        assertEquals(0L, afterComplete.getSuccessfulHelps(),
                "Completing a help must not count it as confirmed");

        // Idempotent retry of complete.
        assertEquals("COMPLETED",
                helpService.completeHelp(accepted.getId(), helper.getId(), false).getStatus());

        // Only the receiver may confirm.
        assertThrows(ForbiddenException.class,
                () -> helpService.confirmHelp(accepted.getId(), helper.getId(), false));

        HelpResponse confirmed = helpService.confirmHelp(accepted.getId(), receiver.getId(), false);
        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(PostStatus.COMPLETED, reloadPost(post).getStatus());

        assertSingleAward(helper.getId(), receiver.getId(), post.getId());

        // Every retry returns the current state and re-awards nothing.
        for (int i = 0; i < 3; i++) {
            assertEquals("CONFIRMED",
                    helpService.confirmHelp(accepted.getId(), receiver.getId(), false).getStatus());
        }
        assertSingleAward(helper.getId(), receiver.getId(), post.getId());

        assertThrows(ConflictException.class,
                () -> helpService.completeHelp(accepted.getId(), helper.getId(), false));
        assertThrows(ConflictException.class,
                () -> helpService.cancelHelp(accepted.getId(), receiver.getId(), false, null));

        List<HelpHistoryEntryDto> history = helpService.getHelpHistory(
                accepted.getId(), helper.getId(), false);
        assertEquals(List.of("ACCEPTED", "COMPLETED", "CONFIRMED"),
                history.stream().map(HelpHistoryEntryDto::getToStatus).collect(Collectors.toList()));
        assertEquals(List.of("HELPER", "HELPER", "RECEIVER"),
                history.stream().map(HelpHistoryEntryDto::getActorRole).collect(Collectors.toList()));

        // Non-participants cannot read the audit trail.
        User outsider = createUser("Lifecycle Outsider");
        assertThrows(ForbiddenException.class,
                () -> helpService.getHelpHistory(accepted.getId(), outsider.getId(), false));
    }

    @Test
    void concurrentConfirmsShouldAwardOnce() throws Exception {
        User helper = createUser("Concurrent Confirm Helper");
        User receiver = createUser("Concurrent Confirm Receiver");
        Post post = createPost(receiver);

        HelpResponse accepted = helpService.acceptHelp(acceptRequest(post), helper.getId());
        helpService.completeHelp(accepted.getId(), helper.getId(), false);

        List<HelpResponse> responses = runConcurrently(CONCURRENT_CONFIRMS,
                () -> helpService.confirmHelp(accepted.getId(), receiver.getId(), false));

        assertEquals(CONCURRENT_CONFIRMS, responses.size());
        responses.forEach(response -> assertEquals("CONFIRMED", response.getStatus()));
        assertSingleAward(helper.getId(), receiver.getId(), post.getId());
    }

    @Test
    void concurrentAcceptsShouldCreateExactlyOneActiveHelp() throws Exception {
        User receiver = createUser("Concurrent Accept Receiver");
        Post post = createPost(receiver);
        List<User> helpers = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_ACCEPTS; i++) {
            helpers.add(createUser("Concurrent Accept Helper " + i));
        }

        List<Object> results = runConcurrentlyAllowFailure(CONCURRENT_ACCEPTS, index ->
                helpService.acceptHelp(acceptRequest(post), helpers.get(index).getId()));

        long successes = results.stream().filter(result -> result instanceof HelpResponse).count();
        long conflicts = results.stream().filter(result -> result instanceof ConflictException).count();

        assertEquals(1, successes, "Exactly one helper must win the race");
        assertEquals(CONCURRENT_ACCEPTS - 1, conflicts,
                "Every losing helper must receive a conflict, not a server error");

        List<Help> active = helpRepository.findByPostId(post.getId()).stream()
                .filter(help -> help.getStatus() == HelpStatus.ACCEPTED
                        || help.getStatus() == HelpStatus.COMPLETED)
                .collect(Collectors.toList());
        assertEquals(1, active.size(), "The database must hold exactly one active help");
        assertEquals(PostStatus.IN_PROGRESS, reloadPost(post).getStatus());
        assertNotNull(reloadPost(post).getHelper());
    }

    @Test
    void cancelPolicyShouldProtectConfirmedHelpAndRecordInitiator() {
        User helper = createUser("Cancel Helper");
        User receiver = createUser("Cancel Receiver");
        Post post = createPost(receiver);

        HelpResponse accepted = helpService.acceptHelp(acceptRequest(post), helper.getId());

        HelpResponse cancelled = helpService.cancelHelp(
                accepted.getId(), receiver.getId(), false, "Передумал");
        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("RECEIVER", cancelled.getCancelInitiator());
        assertEquals("Передумал", cancelled.getCancelReason());

        Post reopened = reloadPost(post);
        assertEquals(PostStatus.OPEN, reopened.getStatus());
        assertNull(reopened.getHelper());

        UserRatingStats stats = statsRepository.findByUserId(helper.getId()).orElseThrow();
        assertEquals(1L, stats.getTotalHelpsGiven());
        assertEquals(1L, stats.getCancelledHelps());
        assertEquals(0L, stats.getSuccessfulHelps());
        assertEquals(0, countTransactions(helper.getId(), "HELP_GIVEN", post.getId()));

        // Cancelling again is idempotent and does not double-count.
        assertEquals("CANCELLED",
                helpService.cancelHelp(accepted.getId(), receiver.getId(), false, null).getStatus());
        assertEquals(1L,
                statsRepository.findByUserId(helper.getId()).orElseThrow().getCancelledHelps());

        List<String> transitions = historyRepository
                .findByHelpIdOrderByCreatedAtAsc(accepted.getId()).stream()
                .map(entry -> entry.getToStatus().name())
                .collect(Collectors.toList());
        assertEquals(List.of("ACCEPTED", "CANCELLED"), transitions);
    }

    @Test
    void receiverCannotCancelCompletedButHelperCan() {
        User helper = createUser("Completed Cancel Helper");
        User receiver = createUser("Completed Cancel Receiver");
        Post post = createPost(receiver);

        HelpResponse first = helpService.acceptHelp(acceptRequest(post), helper.getId());
        helpService.completeHelp(first.getId(), helper.getId(), false);

        assertThrows(ForbiddenException.class,
                () -> helpService.cancelHelp(first.getId(), receiver.getId(), false, null));
        assertEquals(HelpStatus.COMPLETED, reloadHelp(first).getStatus());

        HelpResponse cancelled = helpService.cancelHelp(
                first.getId(), helper.getId(), false, "Не смог довезти");
        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("HELPER", cancelled.getCancelInitiator());
        assertEquals(0, countTransactions(helper.getId(), "HELP_GIVEN", post.getId()));

        // A cancelled help frees the post, and a new helper may accept it.
        User secondHelper = createUser("Second Helper");
        HelpResponse retried = helpService.acceptHelp(acceptRequest(post), secondHelper.getId());
        assertEquals("ACCEPTED", retried.getStatus());
        assertEquals(PostStatus.IN_PROGRESS, reloadPost(post).getStatus());
    }

    @Test
    void adminCancellationRequiresReasonAndCannotCancelConfirmedHelp() {
        User helper = createUser("Admin Cancel Helper");
        User receiver = createUser("Admin Cancel Receiver");
        Post post = createPost(receiver);

        HelpResponse accepted = helpService.acceptHelp(acceptRequest(post), helper.getId());

        assertThrows(BadRequestException.class,
                () -> helpService.cancelHelp(accepted.getId(), 1L, true, "   "));

        HelpResponse cancelled = helpService.cancelHelp(
                accepted.getId(), 1L, true, "Нарушение правил");
        assertEquals("ADMIN", cancelled.getCancelInitiator());
        assertNull(reloadPost(post).getHelper());

        User secondHelper = createUser("Admin Cancel Second Helper");
        HelpResponse second = helpService.acceptHelp(acceptRequest(post), secondHelper.getId());
        helpService.completeHelp(second.getId(), secondHelper.getId(), false);
        helpService.confirmHelp(second.getId(), receiver.getId(), false);
        assertThrows(ConflictException.class,
                () -> helpService.cancelHelp(second.getId(), 1L, true, "Поздно"));
    }

    @Test
    void nonParticipantCannotDriveTheLifecycle() {
        User helper = createUser("Role Helper");
        User receiver = createUser("Role Receiver");
        User outsider = createUser("Role Outsider");
        Post post = createPost(receiver);

        HelpResponse accepted = helpService.acceptHelp(acceptRequest(post), helper.getId());

        assertThrows(ForbiddenException.class,
                () -> helpService.completeHelp(accepted.getId(), receiver.getId(), false));
        assertThrows(ForbiddenException.class,
                () -> helpService.completeHelp(accepted.getId(), outsider.getId(), false));

        helpService.completeHelp(accepted.getId(), helper.getId(), false);

        assertThrows(ForbiddenException.class,
                () -> helpService.confirmHelp(accepted.getId(), helper.getId(), false));
        assertThrows(ForbiddenException.class,
                () -> helpService.confirmHelp(accepted.getId(), outsider.getId(), false));
        assertThrows(ForbiddenException.class,
                () -> helpService.cancelHelp(accepted.getId(), outsider.getId(), false, null));

        assertEquals(HelpStatus.COMPLETED, reloadHelp(accepted).getStatus());
        assertEquals(0, countTransactions(helper.getId(), "HELP_GIVEN", post.getId()));
    }

    @Test
    void slaShouldCancelOnlyStaleAcceptedHelpsAndReopenThePost() {
        User staleHelper = createUser("SLA Stale Helper");
        User receiver = createUser("SLA Receiver");
        Post stalePost = createPost(receiver);
        Post freshPost = createPost(receiver);

        HelpResponse stale = helpService.acceptHelp(acceptRequest(stalePost), staleHelper.getId());
        User freshHelper = createUser("SLA Fresh Helper");
        HelpResponse fresh = helpService.acceptHelp(acceptRequest(freshPost), freshHelper.getId());

        jdbcTemplate.update("UPDATE helps SET accepted_at = ? WHERE id = ?",
                LocalDateTime.now().minusHours(200), stale.getId());

        slaScheduler.cancelStaleAcceptedHelps();

        Help cancelled = reloadHelp(stale);
        assertEquals(HelpStatus.CANCELLED, cancelled.getStatus());
        assertEquals("SYSTEM", cancelled.getCancelInitiator().name());
        assertNotNull(cancelled.getCancelledAt());
        assertEquals(PostStatus.OPEN, reloadPost(stalePost).getStatus());
        assertNull(reloadPost(stalePost).getHelper());

        assertEquals(HelpStatus.ACCEPTED, reloadHelp(fresh).getStatus());
        assertEquals(PostStatus.IN_PROGRESS, reloadPost(freshPost).getStatus());

        List<HelpStatusHistory> history =
                historyRepository.findByHelpIdOrderByCreatedAtAsc(stale.getId());
        HelpStatusHistory last = history.get(history.size() - 1);
        assertEquals(HelpStatus.CANCELLED, last.getToStatus());
        assertEquals("SYSTEM", last.getActorRole().name());
        assertEquals(1L, statsRepository.findByUserId(staleHelper.getId())
                .orElseThrow().getCancelledHelps());
    }

    @Test
    void concurrentCancellationsForTheSameHelperShouldNotLoseStats() throws Exception {
        User helper = createUser("Concurrent Cancel Helper");
        User receiver = createUser("Concurrent Cancel Receiver");
        Post firstPost = createPost(receiver);
        Post secondPost = createPost(receiver);

        HelpResponse first = helpService.acceptHelp(acceptRequest(firstPost), helper.getId());
        HelpResponse second = helpService.acceptHelp(acceptRequest(secondPost), helper.getId());

        List<Object> results = runConcurrentlyAllowFailure(2, index ->
                helpService.cancelHelp(index == 0 ? first.getId() : second.getId(),
                        helper.getId(), false, null));

        results.forEach(result -> assertInstanceOf(HelpResponse.class, result));
        assertEquals(HelpStatus.CANCELLED, reloadHelp(first).getStatus());
        assertEquals(HelpStatus.CANCELLED, reloadHelp(second).getStatus());

        UserRatingStats stats = statsRepository.findByUserId(helper.getId()).orElseThrow();
        assertEquals(2L, stats.getTotalHelpsGiven(),
                "Both cancellation attempts must be counted");
        assertEquals(2L, stats.getCancelledHelps(),
                "Concurrent stats creation must not lose or duplicate a cancellation");
    }

    private void assertSingleAward(Long helperId, Long receiverId, Long postId) {
        assertEquals(1, countTransactions(helperId, "HELP_GIVEN", postId),
                "Exactly one HELP_GIVEN transaction per help");
        assertEquals(1, countTransactions(receiverId, "HELP_RECEIVED", postId),
                "Exactly one HELP_RECEIVED transaction per help");

        UserRatingStats helperStats = statsRepository.findByUserId(helperId).orElseThrow();
        assertEquals(1L, helperStats.getSuccessfulHelps());
        assertEquals(1L, helperStats.getTotalHelpsGiven());
        UserRatingStats receiverStats = statsRepository.findByUserId(receiverId).orElseThrow();
        assertEquals(1L, receiverStats.getTotalHelpsReceived());

        User reloadedHelper = userRepository.findById(helperId).orElseThrow();
        User reloadedReceiver = userRepository.findById(receiverId).orElseThrow();
        assertEquals(1, reloadedHelper.getHelpedCount());
        assertEquals(2, reloadedReceiver.getDebtCount());
    }

    private int countTransactions(Long userId, String type, Long postId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coin_transactions "
                        + "WHERE user_id = ? AND type = ? AND related_post_id = ?",
                Integer.class, userId, type, postId);
        return count != null ? count : 0;
    }

    private Help reloadHelp(HelpResponse response) {
        return helpRepository.findById(response.getId()).orElseThrow();
    }

    private Post reloadPost(Post post) {
        return postRepository.findById(post.getId()).orElseThrow();
    }

    private HelpRequest acceptRequest(Post post) {
        HelpRequest request = new HelpRequest();
        request.setPostId(post.getId());
        return request;
    }

    private User createUser(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail("help-it-" + UUID.randomUUID() + "@test.local");
        user.setPassword("$2a$10$test-only-not-a-real-hash");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.saveAndFlush(user);
    }

    private Post createPost(User author) {
        Post post = new Post();
        post.setTitle("Help lifecycle integration " + UUID.randomUUID());
        post.setDescription("Integration test post");
        post.setCategory("OTHER");
        post.setAuthorName(author.getName());
        post.setUser(author);
        post.setStatus(PostStatus.OPEN);
        post.setDeleted(false);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        return postRepository.saveAndFlush(post);
    }

    private <T> List<T> runConcurrently(int attempts, ThrowingSupplier<T> action) throws Exception {
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

    private List<Object> runConcurrentlyAllowFailure(int attempts, IndexedThrowingSupplier action)
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
    private interface ThrowingSupplier<T> {
        T get();
    }

    @FunctionalInterface
    private interface IndexedThrowingSupplier {
        Object get(int index);
    }
}
