package org.example.helptreeservice.block22;

import org.example.helptreeservice.dto.chat.CreateChatRequest;
import org.example.helptreeservice.dto.chat.CreateMessageRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.dto.posts.PostBoostResult;
import org.example.helptreeservice.dto.reports.CreateReportRequest;
import org.example.helptreeservice.dto.reports.ModerationActionRequest;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.users.BlockUserRequest;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.enums.ModerationActionType;
import org.example.helptreeservice.enums.NotificationType;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 22.9 - cross-feature end-to-end flows.
 *
 * <p>Each scenario walks the same chain a real client follows and then reads
 * the persisted side effects (notifications, ledger, post/help rows). They are
 * intentionally not mocked: the point is to prove that the frontend contract
 * and the backend state agree.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class Block22EndToEndIntegrationTest extends Block22TestSupport {

    @Test
    void reportModerationAndNotificationFlow() {
        User owner = createUser("E2E report owner");
        User reporter = createUser("E2E reporter");
        User admin = createAdmin("E2E report admin");
        Post post = createPost(owner);

        CreateReportRequest request = new CreateReportRequest();
        request.setTargetType(ReportTargetType.POST);
        request.setTargetId(post.getId());
        request.setReason(ReportReason.SPAM);
        request.setDescription("E2E spam report");

        ReportResponse created = reportService.createReport(request, reporter.getId(), null);
        assertEquals(ReportStatus.OPEN, created.getStatus());
        assertEquals(1L, countNotifications(admin.getId(), NotificationType.NEW_REPORT.name()),
                "Every active admin gets exactly one NEW_REPORT notification");

        ModerationActionRequest dismissal = new ModerationActionRequest();
        dismissal.setActionType(ModerationActionType.DISMISS_REPORT);
        dismissal.setReason("No violation found");
        dismissal.setResolution("Reviewed during E2E");
        reportService.applyAction(created.getId(), dismissal, admin.getId());

        ReportResponse stored = reportService.getMyReports(
                        reporter.getId(), null, null, null,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().get(0);
        assertEquals(ReportStatus.REJECTED, stored.getStatus());

        assertEquals(1L, countNotifications(reporter.getId(), NotificationType.REPORT_REJECTED.name()),
                "The reporter is told the outcome of the moderation decision");
        assertTrue(reportService.getMyReport(created.getId(), reporter.getId())
                        .getEvents().stream()
                        .noneMatch(event -> event.getInternalNote() != null),
                "The public event history never leaks internal notes");
    }

    @Test
    void blockPreventsFurtherChatMessages() {
        User alice = createUser("E2E block Alice");
        User bob = createUser("E2E block Bob");
        CreateChatRequest chatRequest = new CreateChatRequest();
        chatRequest.setParticipantId(bob.getId());
        Long chatId = chatService.createChat(chatRequest, alice.getId()).getId();
        chatService.sendMessage(chatId, new CreateMessageRequest("before block", null), alice.getId());

        BlockUserRequest block = new BlockUserRequest();
        block.setScope(UserBlockScope.MESSAGES);
        userBlockService.blockUser(alice.getId(), bob.getId(), block);

        assertThrows(ForbiddenException.class,
                () -> chatService.sendMessage(chatId,
                        new CreateMessageRequest("after block", null), bob.getId()),
                "A MESSAGES block must be enforced on the server for the blocked pair");

        userBlockService.unblockUser(alice.getId(), bob.getId());
        assertNotNull(chatService.sendMessage(chatId,
                new CreateMessageRequest("after unblock", null), bob.getId()));
    }

    @Test
    void helpCompletionConfirmationAndOneTimeAward() {
        User helper = createUser("E2E helper");
        User receiver = createUser("E2E receiver");
        Post post = createPost(receiver);
        HelpResponse accepted = accept(helper, post);

        HelpResponse completed = helpService.completeHelp(accepted.getId(), helper.getId(), false);
        assertEquals(HelpStatus.COMPLETED, HelpStatus.valueOf(completed.getStatus()));
        assertEquals(0L, reload(helper).getHelpCoins(),
                "Completing a help does not pay the helper yet");
        assertEquals(1L, countNotifications(receiver.getId(), NotificationType.HELP_COMPLETED.name()));

        HelpResponse confirmed = helpService.confirmHelp(accepted.getId(), receiver.getId(), false);
        assertEquals(HelpStatus.CONFIRMED, HelpStatus.valueOf(confirmed.getStatus()));

        assertEquals(13L, reload(helper).getHelpCoins(),
                "Helper receives coins-per-help (10) plus the first-help bonus (3)");
        assertEquals(2L, reload(receiver).getHelpCoins(),
                "Receiver gets coins-per-help-received");
        assertEquals(1, countTransactions(helper.getId(), "HELP_GIVEN", post.getId()));
        assertEquals(1, countTransactions(helper.getId(), "FIRST_HELP", post.getId()));
        assertEquals(1, countTransactions(receiver.getId(), "HELP_RECEIVED", post.getId()));
        assertEquals(1L, countNotifications(helper.getId(), NotificationType.HELP_CONFIRMED.name()));
        assertEquals(PostStatus.COMPLETED, reloadPost(post).getStatus());

        // Retrying the confirmation is a no-op, not a second award.
        helpService.confirmHelp(accepted.getId(), receiver.getId(), false);
        assertEquals(13L, reload(helper).getHelpCoins());
        assertEquals(1, countTransactions(helper.getId(), "HELP_GIVEN", post.getId()));
    }

    @Test
    void boostDebitsOnceAndChangesThePost() {
        User owner = createUser("E2E boost owner");
        walletService.adminAddCoins(owner.getId(), owner.getId(), 100L,
                "E2E boost seed", "e2e-boost-seed");
        Post post = createPost(owner);

        PostBoostResult result = postService.boostPost(
                post.getId(), owner.getId(), false, "e2e-boost-key");

        assertTrue(result.applied());
        assertEquals(100L - result.price(), reload(owner).getHelpCoins(),
                "The price reported to the client is the amount actually debited");
        assertEquals(result.balance(), reload(owner).getHelpCoins());
        assertEquals(1, countTransactions(owner.getId(), "POST_BOOST", post.getId()));
        assertEquals(reload(owner).getHelpCoins(), ledgerTotal(owner.getId()));
        assertNotNull(reloadPost(post).getBoostedUntil());
        assertTrue(reloadPost(post).getBoostedUntil().isAfter(java.time.LocalDateTime.now()));

        PostBoostResult replay = postService.boostPost(
                post.getId(), owner.getId(), false, "e2e-boost-key");
        assertEquals(result.price(), replay.price());
        assertEquals(1, countTransactions(owner.getId(), "POST_BOOST", post.getId()),
                "An idempotent replay must not debit twice");
    }
}
