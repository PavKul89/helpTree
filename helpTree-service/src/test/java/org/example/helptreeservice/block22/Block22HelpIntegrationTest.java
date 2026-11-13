package org.example.helptreeservice.block22;

import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 22.2 - help lifecycle edge cases and race proof on a real database.
 *
 * <p>The base lifecycle assertions live in {@code HelpLifecycleIntegrationTest};
 * this class adds the debt threshold, account-block and re-race guarantees that
 * the mandatory checklist calls out explicitly.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class Block22HelpIntegrationTest extends Block22TestSupport {

    private static final int CONCURRENT_ACCEPTS = 6;
    private static final int CONCURRENT_CONFIRMS = 4;

    @Test
    void receiverOverDebtThresholdCannotReceiveHelp() {
        User helper = createUser("Debt helper");
        User receiver = createUser("Debt receiver");
        Post post = createPost(receiver);

        setDebt(receiver, 6, 0);
        assertThrows(BadRequestException.class, () -> accept(helper, post),
                "A receiver with more debt than confirmed helps must be closed for requests");

        // debt == threshold is still allowed.
        setDebt(receiver, 5, 0);
        HelpResponse allowedAtThreshold = accept(helper, post);
        assertEquals("ACCEPTED", allowedAtThreshold.getStatus());

        // Helping enough to cover the debt reopens the account.
        User secondReceiver = createUser("Debt receiver two");
        Post secondPost = createPost(secondReceiver);
        setDebt(secondReceiver, 8, 8);
        assertEquals("ACCEPTED", accept(helper, secondPost).getStatus());
    }

    @Test
    void blockedHelperAndBlockedReceiverCannotAccept() {
        User helper = createUser("Blocked helper");
        User receiver = createUser("Blocked receiver");
        Post post = createPost(receiver);
        blockAccount(helper.getId());

        assertThrows(ForbiddenException.class, () -> accept(helper, post),
                "A debt-blocked helper must not be able to accept work");

        User healthyHelper = createUser("Healthy helper");
        blockAccount(receiver.getId());
        assertThrows(BadRequestException.class, () -> accept(healthyHelper, post),
                "A debt-blocked receiver must not attract new help");
    }

    @Test
    void blockedReceiverConfirmationIsEnforcedByAuthFilterNotByTheService() {
        User helper = createUser("Confirm helper");
        User receiver = createUser("Confirm receiver");
        Post post = createPost(receiver);
        HelpResponse accepted = accept(helper, post);
        helpService.completeHelp(accepted.getId(), helper.getId(), false);

        blockAccount(receiver.getId());
        // JwtAuthFilter rejects a blocked account on the HTTP route. The domain
        // service deliberately still finalizes the help so the helper is paid.
        assertEquals("CONFIRMED",
                helpService.confirmHelp(accepted.getId(), receiver.getId(), false).getStatus());
        assertEquals(HelpStatus.CONFIRMED, helpRepository.findById(accepted.getId()).orElseThrow().getStatus());
    }

    @Test
    void aHelpBlockedByTheReceiverCannotBeAccepted() {
        User helper = createUser("Block aware helper");
        User receiver = createUser("Block aware receiver");
        Post post = createPost(receiver);

        var request = new org.example.helptreeservice.dto.users.BlockUserRequest();
        request.setScope(org.example.helptreeservice.enums.UserBlockScope.HELP_REQUESTS);
        userBlockService.blockUser(receiver.getId(), helper.getId(), request);

        assertThrows(ForbiddenException.class, () -> accept(helper, post));
    }

    @Test
    void concurrentAcceptsCreateExactlyOneActiveHelp() throws Exception {
        User receiver = createUser("Race receiver");
        Post post = createPost(receiver);
        List<User> helpers = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_ACCEPTS; i++) {
            helpers.add(createUser("Race helper " + i));
        }

        List<Object> results = runConcurrentlyAllowFailure(CONCURRENT_ACCEPTS, index ->
                helpService.acceptHelp(helpRequest(post), helpers.get(index).getId()));

        long successes = results.stream().filter(result -> result instanceof HelpResponse).count();
        long conflicts = results.stream().filter(result -> result instanceof ConflictException).count();

        assertEquals(1, successes, "Exactly one helper must win the race");
        assertEquals(CONCURRENT_ACCEPTS - 1, conflicts,
                "Every loser must get a domain conflict instead of a server error");

        long active = helpRepository.findByPostId(post.getId()).stream()
                .filter(help -> help.getStatus() == HelpStatus.ACCEPTED
                        || help.getStatus() == HelpStatus.COMPLETED)
                .count();
        assertEquals(1, active, "The database must hold exactly one active help");
    }

    @Test
    void concurrentConfirmAwardsExactlyOnce() throws Exception {
        User helper = createUser("Race confirm helper");
        User receiver = createUser("Race confirm receiver");
        Post post = createPost(receiver);
        HelpResponse accepted = accept(helper, post);
        helpService.completeHelp(accepted.getId(), helper.getId(), false);

        List<HelpResponse> responses = runConcurrently(CONCURRENT_CONFIRMS,
                () -> helpService.confirmHelp(accepted.getId(), receiver.getId(), false));

        responses.forEach(response -> assertEquals("CONFIRMED", response.getStatus()));
        assertEquals(1, countTransactions(helper.getId(), "HELP_GIVEN", post.getId()),
                "Concurrent confirmations must award the helper exactly once");
        assertEquals(1, countTransactions(receiver.getId(), "HELP_RECEIVED", post.getId()),
                "Concurrent confirmations must award the receiver exactly once");
    }

    @Test
    void monthlyPairLimitSkipsThirdRewardAndResetsForNextMonth() {
        User helper = createUser("Monthly helper");
        User receiver = createUser("Monthly receiver");
        User otherHelper = createUser("Monthly other helper");
        User otherReceiver = createUser("Monthly other receiver");

        HelpResponse first = createConfirmedHelp(helper, receiver);
        HelpResponse second = createConfirmedHelp(helper, receiver);
        HelpResponse third = createConfirmedHelp(helper, receiver);

        assertEquals("CONFIRMED", first.getStatus());
        assertEquals("CONFIRMED", second.getStatus());
        assertEquals("CONFIRMED", third.getStatus());
        assertEquals(2, countTransactions(helper.getId(), "HELP_GIVEN"));
        assertEquals(2, countTransactions(receiver.getId(), "HELP_RECEIVED"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM help_status_history WHERE help_id = ? "
                        + "AND to_status = 'CONFIRMED' AND reason LIKE 'Месячный лимит%'",
                Integer.class, third.getId()));

        createConfirmedHelp(otherHelper, otherReceiver);
        assertEquals(1, countTransactions(otherHelper.getId(), "HELP_GIVEN"),
                "A different helper/receiver pair must have its own monthly limit");

        LocalDateTime previousMonth = YearMonth.from(LocalDateTime.now())
                .minusMonths(1).atDay(15).atStartOfDay();
        int shifted = jdbcTemplate.update(
                "UPDATE helps SET confirmed_at = ? WHERE helper_id = ? AND receiver_id = ? "
                        + "AND status = 'CONFIRMED'",
                previousMonth, helper.getId(), receiver.getId());
        assertEquals(3, shifted);
        setDebt(receiver, 0, 0);

        createConfirmedHelp(helper, receiver);
        createConfirmedHelp(helper, receiver);
        assertEquals(4, countTransactions(helper.getId(), "HELP_GIVEN"),
                "The pair limit must reset when the calendar month changes");
        assertEquals(4, countTransactions(receiver.getId(), "HELP_RECEIVED"));
        assertEquals(ledgerTotal(helper.getId()), reload(helper).getHelpCoins());
        assertEquals(ledgerTotal(receiver.getId()), reload(receiver).getHelpCoins());
    }

    @Test
    void receiverCannotCompleteAndHelperCannotConfirm() {
        User helper = createUser("Role helper");
        User receiver = createUser("Role receiver");
        Post post = createPost(receiver);
        HelpResponse accepted = accept(helper, post);

        assertThrows(ForbiddenException.class,
                () -> helpService.completeHelp(accepted.getId(), receiver.getId(), false));
        helpService.completeHelp(accepted.getId(), helper.getId(), false);
        assertThrows(ForbiddenException.class,
                () -> helpService.confirmHelp(accepted.getId(), helper.getId(), false));

        helpService.confirmHelp(accepted.getId(), receiver.getId(), false);
        assertThrows(ConflictException.class,
                () -> helpService.cancelHelp(accepted.getId(), receiver.getId(), false, null),
                "A confirmed help can never be cancelled");
    }
}
