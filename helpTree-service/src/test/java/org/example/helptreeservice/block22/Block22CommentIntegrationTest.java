package org.example.helptreeservice.block22;

import org.example.helptreeservice.dto.comments.CommentActionResponse;
import org.example.helptreeservice.dto.comments.CommentPageResponse;
import org.example.helptreeservice.dto.comments.CommentResponse;
import org.example.helptreeservice.dto.comments.CreateCommentRequest;
import org.example.helptreeservice.dto.users.BlockUserRequest;
import org.example.helptreeservice.entity.Comment;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.CommentSort;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 22.6 - comment thread integrity, ownership and account-state rules.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class Block22CommentIntegrationTest extends Block22TestSupport {

    @Test
    void aDeletedUserCannotComment() {
        User author = createUser("Comment author");
        User deleted = createUser("Comment deleted");
        Post post = createPost(author);
        markDeleted(deleted.getId());

        assertThrows(NotFoundException.class,
                () -> commentService.createComment(post.getId(), comment("ghost"), deleted.getId()));
        assertEquals(0L, commentCount(post.getId()));
    }

    @Test
    void aBlockedUserCannotComment() {
        User author = createUser("Block comment author");
        User blocked = createUser("Block comment user");
        Post post = createPost(author);
        blockAccount(blocked.getId());

        assertThrows(AccountBlockedException.class,
                () -> commentService.createComment(post.getId(), comment("blocked"), blocked.getId()));
        assertEquals(0L, commentCount(post.getId()));
    }

    @Test
    void aFullBlockByThePostAuthorClosesComments() {
        User author = createUser("Scope comment author");
        User commenter = createUser("Scope comment user");
        Post post = createPost(author);

        BlockUserRequest request = new BlockUserRequest();
        request.setScope(UserBlockScope.ALL);
        userBlockService.blockUser(author.getId(), commenter.getId(), request);

        assertThrows(ForbiddenException.class,
                () -> commentService.createComment(post.getId(), comment("not allowed"), commenter.getId()));

        userBlockService.unblockUser(author.getId(), commenter.getId());
        CommentActionResponse allowed = commentService.createComment(
                post.getId(), comment("allowed again"), commenter.getId());
        assertNotNull(allowed.getComment().getId());
    }

    @Test
    void aParentCommentMustBelongToTheSamePost() {
        User author = createUser("Parent author");
        User commenter = createUser("Parent commenter");
        Post first = createPost(author);
        Post second = createPost(author);

        CommentActionResponse root = commentService.createComment(
                first.getId(), comment("root of first"), commenter.getId());

        CreateCommentRequest crossPost = comment("cross post reply");
        crossPost.setParentCommentId(root.getComment().getId());
        assertThrows(BadRequestException.class,
                () -> commentService.createComment(second.getId(), crossPost, commenter.getId()),
                "A reply may not cross the post boundary");
    }

    @Test
    void aCommentCannotBeDeletedThroughAnotherPostUrl() {
        User author = createUser("Delete comment author");
        User commenter = createUser("Delete comment user");
        Post first = createPost(author);
        Post second = createPost(author);

        CommentActionResponse root = commentService.createComment(
                first.getId(), comment("stays put"), commenter.getId());

        assertThrows(NotFoundException.class,
                () -> commentService.deleteComment(second.getId(), root.getComment().getId(),
                        commenter.getId()),
                "The comment must be resolved inside the post id from the URL");
        assertFalse(commentRepository.findById(root.getComment().getId()).orElseThrow().getDeleted());
    }

    @Test
    void onlyTheAuthorOrAnAdminCanDeleteAComment() {
        User author = createUser("Ownership comment author");
        User commenter = createUser("Ownership commenter");
        User outsider = createUser("Ownership outsider");
        User admin = createAdmin("Ownership admin");
        Post post = createPost(author);
        CommentActionResponse comment = commentService.createComment(
                post.getId(), comment("mine"), commenter.getId());

        assertThrows(ForbiddenException.class,
                () -> commentService.deleteComment(post.getId(), comment.getComment().getId(),
                        outsider.getId()));

        commentService.deleteComment(post.getId(), comment.getComment().getId(), admin.getId());
        Comment stored = commentRepository.findById(comment.getComment().getId()).orElseThrow();
        assertTrue(stored.getDeleted());
        assertFalse(stored.getDeletedByAuthor(),
                "An administrative removal is not attributed to the author");
    }

    @Test
    void softDeleteKeepsTheTreeAndRepliesVisible() {
        User author = createUser("Tree author");
        User commenter = createUser("Tree commenter");
        Post post = createPost(author);

        CommentActionResponse root = commentService.createComment(
                post.getId(), comment("root"), commenter.getId());
        CreateCommentRequest replyRequest = comment("reply");
        replyRequest.setParentCommentId(root.getComment().getId());
        CommentActionResponse reply = commentService.createComment(
                post.getId(), replyRequest, createUser("Tree replier").getId());

        assertEquals(2L, commentCount(post.getId()));

        commentService.deleteComment(post.getId(), root.getComment().getId(), commenter.getId());

        CommentPageResponse page = commentService.getCommentsByPost(
                post.getId(), 0, 20, CommentSort.OLDEST);
        List<CommentResponse> content = page.getContent();
        CommentResponse deletedRoot = content.stream()
                .filter(item -> item.getId().equals(root.getComment().getId()))
                .findFirst().orElseThrow();
        assertTrue(deletedRoot.isDeleted(), "The deleted root must stay in the thread as a placeholder");
        assertTrue(content.stream().anyMatch(item -> item.getId().equals(reply.getComment().getId())),
                "Soft-deleting a parent must not hide its replies");
        assertEquals(1L, page.getTotalComments(), "The visible counter only counts live comments");
        assertEquals(1L, commentCount(post.getId()),
                "posts.comments_count follows the same projection");

        CreateCommentRequest replyToDeleted = comment("cannot reply");
        replyToDeleted.setParentCommentId(root.getComment().getId());
        assertThrows(BadRequestException.class,
                () -> commentService.createComment(post.getId(), replyToDeleted,
                        createUser("Tree late replier").getId()));
    }

    @Test
    void postCommentCounterCannotDriftBelowZeroOnRepeatedDelete() {
        User author = createUser("Counter author");
        User commenter = createUser("Counter commenter");
        Post post = createPost(author);
        CommentActionResponse comment = commentService.createComment(
                post.getId(), comment("counted once"), commenter.getId());
        assertEquals(1L, commentCount(post.getId()));

        commentService.deleteComment(post.getId(), comment.getComment().getId(), commenter.getId());
        commentService.deleteComment(post.getId(), comment.getComment().getId(), commenter.getId());
        assertEquals(0L, commentCount(post.getId()),
                "Repeating a delete must not decrement the counter twice");
    }

    // --------------------------------------------------------------- helpers

    private CreateCommentRequest comment(String content) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent(content);
        return request;
    }

    private long commentCount(Long postId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT comments_count FROM posts WHERE id = ?", Long.class, postId);
        return count != null ? count : 0L;
    }
}
