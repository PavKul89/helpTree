package org.example.helptreeservice.block22;

import org.example.helptreeservice.dto.RatingResponse;
import org.example.helptreeservice.dto.reviews.CreateReviewRequest;
import org.example.helptreeservice.dto.reviews.ReplyReviewRequest;
import org.example.helptreeservice.dto.reviews.ReviewResponse;
import org.example.helptreeservice.dto.reviews.UpdateReviewRequest;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 22.5 - reviews, rating projection, one-time reward and cache eviction.
 *
 * <p>All assertions read persisted rows: the wallet ledger, the
 * {@code user_rating_stats} projection and the review table itself.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class Block22ReviewIntegrationTest extends Block22TestSupport {

    @Autowired private CacheManager cacheManager;

    @Test
    void onlyConfirmedHelpParticipantsCanReview() {
        User helper = createUser("Review helper");
        User receiver = createUser("Review receiver");
        User outsider = createUser("Review outsider");
        Post post = createPost(receiver);

        var accepted = accept(helper, post);
        CreateReviewRequest beforeConfirm = reviewRequest(accepted.getId(), 5, "too early");
        assertThrows(BadRequestException.class,
                () -> reviewService.createReview(beforeConfirm, receiver.getId()),
                "A review is only allowed after the help is confirmed");

        helpService.completeHelp(accepted.getId(), helper.getId(), false);
        helpService.confirmHelp(accepted.getId(), receiver.getId(), false);

        assertThrows(ForbiddenException.class,
                () -> reviewService.createReview(reviewRequest(accepted.getId(), 5, "outsider"),
                        outsider.getId()),
                "Only the two participants of the confirmed help may review");

        ReviewResponse fromReceiver = reviewService.createReview(
                reviewRequest(accepted.getId(), 5, "great help"), receiver.getId());
        assertEquals(helper.getId(), fromReceiver.getToUserId());
        assertEquals(receiver.getId(), fromReceiver.getFromUserId());

        ReviewResponse fromHelper = reviewService.createReview(
                reviewRequest(accepted.getId(), 4, "good receiver"), helper.getId());
        assertEquals(receiver.getId(), fromHelper.getToUserId(),
                "Each participant may review the other exactly once");
    }

    @Test
    void aSecondReviewForTheSameHelpIsRejectedEvenAfterDeletion() {
        User helper = createUser("Unique helper");
        User receiver = createUser("Unique receiver");
        var help = createConfirmedHelp(helper, receiver);

        ReviewResponse review = reviewService.createReview(
                reviewRequest(help.getId(), 5, "first"), receiver.getId());

        assertThrows(BadRequestException.class,
                () -> reviewService.createReview(reviewRequest(help.getId(), 4, "second"),
                        receiver.getId()),
                "A participant may write only one review per help");

        reviewService.deleteReview(review.getId(), receiver.getId());
        assertThrows(BadRequestException.class,
                () -> reviewService.createReview(reviewRequest(help.getId(), 4, "after delete"),
                        receiver.getId()),
                "The one-review invariant is kept by the unique index, so deletion must not reopen it");
    }

    @Test
    void theRecipientReceivesTheReviewBonusExactlyOnce() {
        User helper = createUser("Bonus helper");
        User receiver = createUser("Bonus receiver");
        var help = createConfirmedHelp(helper, receiver);

        long balanceBefore = reload(helper).getHelpCoins();
        ReviewResponse review = reviewService.createReview(
                reviewRequest(help.getId(), 5, "paid once"), receiver.getId());

        User reloadedHelper = reload(helper);
        assertEquals(balanceBefore + 2L, reloadedHelper.getHelpCoins(),
                "The review reward is exactly coins-per-review");
        assertEquals(1, countTransactions(helper.getId(), "REVIEW_BONUS"),
                "Only one ledger row may be written for one review");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coin_transactions WHERE user_id = ? "
                        + "AND type = 'REVIEW_BONUS' AND reference_id LIKE ?",
                Long.class, helper.getId(), "%review:" + review.getId()));
        assertEquals(reloadedHelper.getHelpCoins(), ledgerTotal(helper.getId()),
                "The ledger must equal the materialized balance");
    }

    @Test
    void creatingAndDeletingAReviewRecalculatesTheRatingAndEvictsTheCache() {
        User helper = createUser("Rating helper");
        User receiver = createUser("Rating receiver");
        var help = createConfirmedHelp(helper, receiver);

        // 3.0 base + 0.1 for one confirmed help.
        RatingResponse afterHelp = ratingService.getUserRating(helper.getId());
        assertEquals(3.1, afterHelp.getOverallRating(), 0.0001);
        Cache cache = cacheManager.getCache("userRating");
        assertNotNull(cache);
        assertNotNull(cache.get(helper.getId()), "The first read must populate the cache");

        ReviewResponse review = reviewService.createReview(
                reviewRequest(help.getId(), 5, "five stars"), receiver.getId());
        assertNull(cache.get(helper.getId()),
                "Creating a review must evict the cached rating after commit");

        RatingResponse afterReview = ratingService.getUserRating(helper.getId());
        assertEquals(3.3, afterReview.getOverallRating(), 0.0001,
                "3.1 help bonus + (5-3)*0.5*(1/5) review contribution");
        assertEquals(1L, afterReview.getReviewCount());

        reviewService.deleteReview(review.getId(), receiver.getId());
        assertNull(cache.get(helper.getId()),
                "Deleting a review must evict the cached rating after commit");
        RatingResponse afterDelete = ratingService.getUserRating(helper.getId());
        assertEquals(3.1, afterDelete.getOverallRating(), 0.0001,
                "A soft-deleted review stops counting immediately");
        assertEquals(0L, afterDelete.getReviewCount());
    }

    @Test
    void editingAReviewRecalculatesTheRating() {
        User helper = createUser("Edit helper");
        User receiver = createUser("Edit receiver");
        var help = createConfirmedHelp(helper, receiver);
        ReviewResponse review = reviewService.createReview(
                reviewRequest(help.getId(), 5, "five"), receiver.getId());
        assertEquals(3.3, ratingService.getUserRating(helper.getId()).getOverallRating(), 0.0001);

        UpdateReviewRequest update = new UpdateReviewRequest();
        update.setRating(1);
        reviewService.updateReview(review.getId(), update, receiver.getId());

        double expected = 3.1 - (2.0 * 0.5 * 0.2);
        assertEquals(expected, ratingService.getUserRating(helper.getId()).getOverallRating(), 0.0001,
                "Editing the stored rating must move the aggregate");
        assertEquals(1, countTransactions(helper.getId(), "REVIEW_BONUS"),
                "Editing a review must never pay a second bonus");

        assertThrows(ForbiddenException.class, () -> reviewService.updateReview(
                review.getId(), update, helper.getId()),
                "Only the author may edit the review");
    }

    @Test
    void onlyTheRecipientMayReplyAndOnlyTheAuthorMayDelete() {
        User helper = createUser("Reply helper");
        User receiver = createUser("Reply receiver");
        User outsider = createUser("Reply outsider");
        var help = createConfirmedHelp(helper, receiver);
        ReviewResponse review = reviewService.createReview(
                reviewRequest(help.getId(), 5, "review"), receiver.getId());

        ReplyReviewRequest reply = new ReplyReviewRequest();
        reply.setReplyText("thank you");
        assertThrows(ForbiddenException.class,
                () -> reviewService.replyToReview(review.getId(), reply, receiver.getId()),
                "The review author cannot reply to their own review");
        assertThrows(ForbiddenException.class,
                () -> reviewService.replyToReview(review.getId(), reply, outsider.getId()));

        ReviewResponse replied = reviewService.replyToReview(review.getId(), reply, helper.getId());
        assertEquals("thank you", replied.getReplyText());
        assertThrows(ForbiddenException.class,
                () -> reviewService.deleteReview(review.getId(), helper.getId()),
                "The recipient cannot delete the review they received");

        reviewService.deleteReview(review.getId(), receiver.getId());
        assertTrue(reviewRepository.findById(review.getId()).orElseThrow().getDeleted());
        assertThrows(NotFoundException.class,
                () -> reviewService.deleteReview(review.getId(), receiver.getId()),
                "Deleting twice is a not-found, not a silent success");
        assertEquals(0, reviewService.getReviewsByUser(helper.getId(), PageRequest.of(0, 10))
                .getContent().size());
    }

    @Test
    void administrativeDeletionRequiresAdminAndAReason() {
        User helper = createUser("Admin delete helper");
        User receiver = createUser("Admin delete receiver");
        User admin = createAdmin("Review admin");
        User regular = createUser("Regular user");
        var help = createConfirmedHelp(helper, receiver);
        ReviewResponse review = reviewService.createReview(
                reviewRequest(help.getId(), 5, "to moderate"), receiver.getId());

        assertThrows(ForbiddenException.class,
                () -> reviewService.adminDeleteReview(review.getId(), regular.getId(), "not an admin"));
        assertThrows(BadRequestException.class,
                () -> reviewService.adminDeleteReview(review.getId(), admin.getId(), "  "),
                "Every moderation action is audited and needs a reason");

        reviewService.adminDeleteReview(review.getId(), admin.getId(), "violates guidelines");
        Review stored = reviewRepository.findById(review.getId()).orElseThrow();
        assertTrue(stored.getDeleted());
        assertFalse(reviewService.getReviewsByUser(helper.getId(), PageRequest.of(0, 10))
                .getContent().stream().anyMatch(item -> item.getId().equals(review.getId())));
        assertEquals(3.1, ratingService.getUserRating(helper.getId()).getOverallRating(), 0.0001);
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_log WHERE admin_id = ? AND entity_id = ? "
                        + "AND action = 'REVIEW_DELETED'",
                Long.class, admin.getId(), review.getId()));
    }

    @Test
    void theTopRatingListNeverContainsNullUsers() {
        User rated = createUser("Top rated");
        var help = createConfirmedHelp(rated, createUser("Top receiver"));
        reviewService.createReview(reviewRequest(help.getId(), 5, "top"), rated.getId());
        // The helper wrote the review above; make sure both sides have a row.
        helpRepository.findById(help.getId()).orElseThrow();

        List<RatingResponse> top = ratingService.getTopRatedUsers(PageRequest.of(0, 100)).getContent();
        assertFalse(top.isEmpty());
        assertTrue(top.stream().noneMatch(java.util.Objects::isNull),
                "Deleted users are filtered out instead of producing null entries");
        assertTrue(top.stream().allMatch(item -> item.getUserId() != null && item.getUserName() != null));
    }

    // --------------------------------------------------------------- helpers

    private CreateReviewRequest reviewRequest(Long helpId, int rating, String comment) {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setHelpId(helpId);
        request.setRating(rating);
        request.setComment(comment);
        return request;
    }
}
