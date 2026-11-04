package org.example.helptreeservice.comment;

import org.example.helptreeservice.dto.comments.CommentActionResponse;
import org.example.helptreeservice.dto.comments.CommentPageResponse;
import org.example.helptreeservice.dto.comments.CommentResponse;
import org.example.helptreeservice.dto.comments.CreateCommentRequest;
import org.example.helptreeservice.dto.reports.CreateCommentReportRequest;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.reports.ResolveReportRequest;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.CommentSort;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.enums.ReportAction;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.CommentService;
import org.example.helptreeservice.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-database proof of the Block 8 comment contract: the recursive thread
 * query returns complete branches, tombstones never destroy replies, the post
 * counter follows non-deleted comments and a report resolution soft-deletes
 * the target through the V42 {@code reports} table.
 *
 * <p>The test commits data and removes only rows belonging to its own uniquely
 * named users. Run with {@code INTEGRATION_TEST=true}.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class CommentThreadIntegrationTest {

    @Autowired private CommentService commentService;
    @Autowired private ReportService reportService;
    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email LIKE 'comment-it-%@test.local'", Long.class);
        if (ids.isEmpty()) {
            return;
        }

        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM reports WHERE reporter_id IN (" + in + ") OR resolved_by_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM comments WHERE user_id IN (" + in + ") OR deleted_by_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM posts WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (" + in + ")");
    }

    @Test
    void paginationReturnsWholeBranchesAndTombstonesKeepReplies() {
        User author = createUser("Comment Author", Role.USER);
        Post post = createPost(author);

        long root1 = createComment(post, author, "root-1", null);
        long reply1 = createComment(post, author, "reply-1", root1);
        long grandchild = createComment(post, author, "grandchild", reply1);
        long root2 = createComment(post, author, "root-2", null);
        long root3 = createComment(post, author, "root-3", null);

        CommentPageResponse firstPage = commentService.getCommentsByPost(post.getId(), 0, 2, CommentSort.OLDEST);
        assertEquals(3, firstPage.getTotalElements(), "totalElements counts roots only");
        assertTrue(firstPage.isHasMore());
        assertEquals(5, firstPage.getTotalComments());

        List<Long> firstIds = firstPage.getContent().stream().map(CommentResponse::getId).toList();
        assertEquals(List.of(root1, reply1, grandchild, root2), firstIds,
                "a page contains its roots and every descendant, ordered by creation");
        assertParentsAreLoaded(firstPage.getContent());

        // Author delete is a tombstone: the reply below it must survive.
        CommentActionResponse deleted = commentService.deleteComment(post.getId(), reply1, author.getId());
        assertTrue(deleted.getComment().isDeleted());
        assertTrue(deleted.getComment().isDeletedByAuthor());
        assertNull(deleted.getComment().getContent());
        assertEquals(4, deleted.getTotalComments());

        CommentPageResponse afterDelete = commentService.getCommentsByPost(post.getId(), 0, 2, CommentSort.OLDEST);
        List<Long> afterIds = afterDelete.getContent().stream().map(CommentResponse::getId).toList();
        assertTrue(afterIds.containsAll(List.of(root1, reply1, grandchild, root2)),
                "the deleted node and its child remain in the tree");
        CommentResponse tombstone = afterDelete.getContent().stream()
                .filter(comment -> comment.getId().equals(reply1))
                .findFirst()
                .orElseThrow();
        assertTrue(tombstone.isDeleted());
        assertNull(tombstone.getContent());
        assertEquals(4, afterDelete.getTotalComments());

        // Deleting the root tombstone still keeps the grandchild reachable.
        commentService.deleteComment(post.getId(), root1, author.getId());
        CommentPageResponse afterRootDelete = commentService.getCommentsByPost(post.getId(), 0, 2, CommentSort.OLDEST);
        assertTrue(afterRootDelete.getContent().stream().map(CommentResponse::getId).toList().contains(grandchild));
        assertEquals(3, afterRootDelete.getTotalComments());
        assertEquals(3, commentsCount(post.getId()));
    }

    @Test
    void replyBeyondMaxDepthIsRejected() {
        User author = createUser("Depth Author", Role.USER);
        Post post = createPost(author);

        long parent = createComment(post, author, "depth-0", null);
        for (int depth = 1; depth <= 3; depth++) {
            parent = createComment(post, author, "depth-" + depth, parent);
        }

        long deepest = parent;
        assertThrows(BadRequestException.class,
                () -> createComment(post, author, "too-deep", deepest));
    }

    @Test
    void resolvingReportSoftDeletesCommentAndUpdatesCounter() {
        User author = createUser("Reported Author", Role.USER);
        User reporter = createUser("Reporter", Role.USER);
        User admin = createUser("Moderator", Role.ADMIN);
        Post post = createPost(author);
        long commentId = createComment(post, author, "reported comment", null);

        CreateCommentReportRequest request = new CreateCommentReportRequest();
        request.setReason(ReportReason.ABUSE);
        request.setDetails("  insult\u0000  ");
        ReportResponse report = reportService.createCommentReport(
                post.getId(), commentId, request, reporter.getId());

        assertEquals(ReportStatus.OPEN, report.getStatus());
        assertEquals("insult", report.getDetails());

        ResolveReportRequest resolve = new ResolveReportRequest();
        resolve.setAction(ReportAction.DELETE_COMMENT);
        resolve.setResolution("Нарушение правил");
        ReportResponse resolved = reportService.resolveReport(report.getId(), resolve, admin.getId());

        assertEquals(ReportStatus.RESOLVED, resolved.getStatus());
        assertEquals(admin.getId(), resolved.getResolvedById());
        assertEquals(0, commentsCount(post.getId()), "moderation delete decrements the post counter");

        CommentPageResponse comments = commentService.getCommentsByPost(
                post.getId(), 0, 20, CommentSort.OLDEST);
        assertEquals(1, comments.getContent().size());
        CommentResponse tombstone = comments.getContent().get(0);
        assertTrue(tombstone.isDeleted());
        assertFalse(tombstone.isDeletedByAuthor());
        assertNull(tombstone.getContent());
        assertEquals(0, comments.getTotalComments());
    }

    private void assertParentsAreLoaded(List<CommentResponse> content) {
        Set<Long> ids = content.stream().map(CommentResponse::getId).collect(Collectors.toSet());
        content.stream()
                .filter(comment -> comment.getParentCommentId() != null)
                .forEach(comment -> assertTrue(ids.contains(comment.getParentCommentId()),
                        "every reply on a page must have its parent on the same page"));
    }

    private long createComment(Post post, User author, String content, Long parentId) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent(content);
        request.setParentCommentId(parentId);
        return commentService.createComment(post.getId(), request, author.getId())
                .getComment()
                .getId();
    }

    private int commentsCount(Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT comments_count FROM posts WHERE id = ?", Integer.class, postId);
    }

    private User createUser(String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail("comment-it-" + UUID.randomUUID() + "@test.local");
        user.setPassword("$2a$10$test-only-not-a-real-hash");
        user.setRole(role);
        user.setDeleted(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.saveAndFlush(user);
    }

    private Post createPost(User author) {
        Post post = new Post();
        post.setTitle("Comment integration " + UUID.randomUUID());
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
}
