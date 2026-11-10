package org.example.helptreeservice.block22;

import jakarta.servlet.FilterChain;
import org.example.helptreeservice.config.RateLimitConfig;
import org.example.helptreeservice.dto.reports.AdminReportDetailsResponse;
import org.example.helptreeservice.dto.reports.AssignReportRequest;
import org.example.helptreeservice.dto.reports.CreateReportRequest;
import org.example.helptreeservice.dto.reports.ModerationActionRequest;
import org.example.helptreeservice.dto.reports.ReportDetailsResponse;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.reports.UpdateReportRequest;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.ModerationActionType;
import org.example.helptreeservice.enums.ReportEventType;
import org.example.helptreeservice.enums.ReportPriority;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.filter.RateLimiterFilter;
import org.example.helptreeservice.security.ClientInfoResolver;
import org.example.helptreeservice.service.ratelimit.DistributedRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 22.7 - report lifecycle, ownership, moderation queue, internal notes,
 * idempotency and the distributed rate limiter.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class Block22ReportIntegrationTest extends Block22TestSupport {

    @Autowired private DistributedRateLimiter distributedRateLimiter;
    @Autowired private ClientInfoResolver clientInfoResolver;

    @Test
    void aUserCanCreateAndReadOnlyTheirOwnReport() {
        User owner = createUser("Reported owner");
        User reporter = createUser("Report reporter");
        User outsider = createUser("Report outsider");
        Post post = createPost(owner);

        ReportResponse created = reportService.createReport(
                report(ReportTargetType.POST, post.getId(), ReportReason.SPAM, "spam post"),
                reporter.getId(), "b22-report-own");

        assertEquals(ReportStatus.OPEN, created.getStatus());
        assertEquals(reporter.getId(), created.getReporterId());

        ReportDetailsResponse details = reportService.getMyReport(created.getId(), reporter.getId());
        assertFalse(details.getEvents().isEmpty());
        assertTrue(details.getEvents().stream().allMatch(event -> event.getInternalNote() == null),
                "A normal user never receives an internal staff note");
        assertTrue(details.getEvents().stream().anyMatch(event -> event.getEventType() == ReportEventType.CREATED));

        assertThrows(NotFoundException.class,
                () -> reportService.getMyReport(created.getId(), outsider.getId()),
                "A foreign report must look like a missing one");

        assertEquals(1, reportService.getMyReports(reporter.getId(), null, null, null,
                PageRequest.of(0, 10)).getContent().size());

        assertThrows(BadRequestException.class,
                () -> reportService.createReport(
                        report(ReportTargetType.POST, post.getId(), ReportReason.SPAM, "self"),
                        owner.getId(), null),
                "A user cannot report their own object");
    }

    @Test
    void aClosedObjectCannotBeReportedButAnOpenOneCan() {
        User owner = createUser("Closed owner");
        User reporter = createUser("Closed reporter");
        Post post = createPost(owner);

        jdbcTemplate.update("UPDATE posts SET deleted = TRUE, deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                post.getId());
        assertThrows(NotFoundException.class,
                () -> reportService.createReport(
                        report(ReportTargetType.POST, post.getId(), ReportReason.ABUSE, null),
                        reporter.getId(), null),
                "Closed content is reported as not found, never disclosed");
    }

    @Test
    void duplicateOpenReportsAreRejectedAndReopenAfterResolution() {
        User owner = createUser("Duplicate report owner");
        User reporter = createUser("Duplicate report reporter");
        User admin = createAdmin("Duplicate report admin");
        Post post = createPost(owner);

        ReportResponse first = reportService.createReport(
                report(ReportTargetType.POST, post.getId(), ReportReason.SPAM, null),
                reporter.getId(), null);

        assertThrows(ConflictException.class,
                () -> reportService.createReport(
                        report(ReportTargetType.POST, post.getId(), ReportReason.ABUSE, null),
                        reporter.getId(), null),
                "A reporter may keep only one open report per target");

        dismiss(first.getId(), admin);
        ReportResponse reopened = reportService.createReport(
                report(ReportTargetType.POST, post.getId(), ReportReason.FRAUD, null),
                reporter.getId(), null);
        assertNotNull(reopened.getId());
        assertTrue(reopened.getId() > first.getId());
    }

    @Test
    void anIdempotencyKeyReturnsTheStoredReport() {
        User owner = createUser("Idempotent owner");
        User reporter = createUser("Idempotent reporter");
        Post post = createPost(owner);

        ReportResponse first = reportService.createReport(
                report(ReportTargetType.POST, post.getId(), ReportReason.SPAM, null),
                reporter.getId(), "b22-report-idem");

        ReportResponse replay = reportService.createReport(
                report(ReportTargetType.POST, post.getId(), ReportReason.OTHER, "ignored"),
                reporter.getId(), "b22-report-idem");

        assertEquals(first.getId(), replay.getId(), "The same key must return the same row");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE reporter_id = ? AND idempotency_key = ?",
                Long.class, reporter.getId(), "b22-report-idem"));
    }

    @Test
    void internalNotesAreOnlyVisibleInTheAdminView() {
        User owner = createUser("Note owner");
        User reporter = createUser("Note reporter");
        User admin = createAdmin("Note admin");
        Post post = createPost(owner);
        ReportResponse created = reportService.createReport(
                report(ReportTargetType.POST, post.getId(), ReportReason.SPAM, null),
                reporter.getId(), null);

        UpdateReportRequest update = new UpdateReportRequest();
        update.setPriority(ReportPriority.HIGH);
        update.setInternalNote("internal-only-detail");
        reportService.updateReport(created.getId(), update, admin.getId());

        ReportDetailsResponse asUser = reportService.getMyReport(created.getId(), reporter.getId());
        assertTrue(asUser.getEvents().stream().noneMatch(event -> event.getEventType() == ReportEventType.NOTE),
                "NOTE events are staff-only and filtered out for the reporter");
        assertNull(asUser.getReport().getResolution());

        AdminReportDetailsResponse asAdmin = reportService.getAdminReport(created.getId());
        assertEquals("internal-only-detail", asAdmin.getReport().getInternalNote());
        assertTrue(asAdmin.getEvents().stream()
                .anyMatch(event -> event.getEventType() == ReportEventType.NOTE
                        && "internal-only-detail".equals(event.getInternalNote())));
    }

    @Test
    void statusChangesWriteHistoryAndAClosedReportIsImmutable() {
        User owner = createUser("Status owner");
        User reporter = createUser("Status reporter");
        User admin = createAdmin("Status admin");
        Post post = createPost(owner);
        ReportResponse created = reportService.createReport(
                report(ReportTargetType.POST, post.getId(), ReportReason.SPAM, null),
                reporter.getId(), null);

        var assigned = reportService.assignReport(
                created.getId(), new AssignReportRequest(), admin.getId());
        assertEquals(ReportStatus.IN_REVIEW, assigned.getStatus());
        assertEquals(admin.getId(), assigned.getAssignedModeratorId());

        // A repeated identical assignment is a no-op.
        var repeated = reportService.assignReport(
                created.getId(), new AssignReportRequest(), admin.getId());
        assertEquals(ReportStatus.IN_REVIEW, repeated.getStatus());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_events WHERE report_id = ? AND event_type = 'ASSIGNED'",
                Long.class, created.getId()));

        dismiss(created.getId(), admin);

        assertTrue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_events WHERE report_id = ? AND event_type = 'RESOLVED'",
                Long.class, created.getId()) >= 1L);
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM moderation_actions WHERE report_id = ?",
                Long.class, created.getId()));
        assertEquals(ReportStatus.REJECTED, reportRepository.findById(created.getId())
                .orElseThrow().getStatus());

        ModerationActionRequest again = dismissAction();
        assertThrows(ConflictException.class,
                () -> reportService.applyAction(created.getId(), again, admin.getId()),
                "A closed report cannot be actioned twice");
        assertThrows(ConflictException.class,
                () -> reportService.assignReport(created.getId(), new AssignReportRequest(), admin.getId()),
                "A closed report cannot be reassigned");
    }

    @Test
    void onlyAdminsCanOperateTheModerationQueue() {
        User owner = createUser("Rights owner");
        User reporter = createUser("Rights reporter");
        User regular = createAdmin("Rights regular");
        jdbcTemplate.update("UPDATE users SET role = 'USER' WHERE id = ?", regular.getId());
        regular = reload(regular);
        Post post = createPost(owner);
        ReportResponse created = reportService.createReport(
                report(ReportTargetType.POST, post.getId(), ReportReason.SPAM, null),
                reporter.getId(), null);

        UpdateReportRequest update = new UpdateReportRequest();
        update.setPriority(ReportPriority.URGENT);
        final User nonAdmin = regular;
        assertThrows(ForbiddenException.class,
                () -> reportService.updateReport(created.getId(), update, nonAdmin.getId()));
        assertThrows(ForbiddenException.class,
                () -> reportService.assignReport(created.getId(), new AssignReportRequest(), nonAdmin.getId()));
        assertThrows(ForbiddenException.class,
                () -> reportService.applyAction(created.getId(), dismissAction(), nonAdmin.getId()));
    }

    @Test
    void blockedAndDeletedUsersCannotCreateReports() {
        User owner = createUser("State owner");
        User blocked = createUser("State blocked");
        User deleted = createUser("State deleted");
        Post post = createPost(owner);

        blockAccount(blocked.getId());
        assertThrows(AccountBlockedException.class,
                () -> reportService.createReport(
                        report(ReportTargetType.POST, post.getId(), ReportReason.SPAM, null),
                        blocked.getId(), null));

        markDeleted(deleted.getId());
        assertThrows(NotFoundException.class,
                () -> reportService.createReport(
                        report(ReportTargetType.POST, post.getId(), ReportReason.SPAM, null),
                        deleted.getId(), null));
    }

    @Test
    void theReportEndpointIsRateLimitedByTheSharedStore() throws Exception {
        User owner = createUser("Rate owner");
        User reporter = createUser("Rate reporter");
        Post post = createPost(owner);

        RateLimitConfig config = new RateLimitConfig();
        config.setEnabled(true);
        config.setCreateLimit(2);
        config.setWindowSeconds(60);
        RateLimiterFilter filter = new RateLimiterFilter(config, distributedRateLimiter, clientInfoResolver);

        String ip = "203.0.113.22";
        String account = "u" + reporter.getId();
        String bucketKey = "rl:write:/api/reports:" + ip + ":" + account;
        distributedRateLimiter.reset(bucketKey);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reports");
        request.setRemoteAddr(ip);
        request.setAttribute("X-User-Id", String.valueOf(reporter.getId()));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse first = invoke(filter, request, chain);
        MockHttpServletResponse second = invoke(filter, request, chain);
        MockHttpServletResponse third = invoke(filter, request, chain);

        assertEquals(200, first.getStatus());
        assertEquals("2", first.getHeader("X-RateLimit-Limit"));
        assertEquals("1", first.getHeader("X-RateLimit-Remaining"));
        assertEquals(200, second.getStatus());
        assertEquals("0", second.getHeader("X-RateLimit-Remaining"));
        assertEquals(429, third.getStatus(), "The third write in the window must be rejected");
        assertNotNull(third.getHeader("Retry-After"));
        assertTrue(third.getContentAsString().contains("TOO_MANY_REQUESTS"));
        verify(chain, times(2)).doFilter(any(), any());

        distributedRateLimiter.reset(bucketKey);
    }

    // --------------------------------------------------------------- helpers

    private CreateReportRequest report(ReportTargetType type, Long targetId,
                                       ReportReason reason, String description) {
        CreateReportRequest request = new CreateReportRequest();
        request.setTargetType(type);
        request.setTargetId(targetId);
        request.setReason(reason);
        request.setDescription(description);
        return request;
    }

    private ModerationActionRequest dismissAction() {
        ModerationActionRequest request = new ModerationActionRequest();
        request.setActionType(ModerationActionType.DISMISS_REPORT);
        request.setReason("No violation found");
        request.setResolution("Dismissed by test");
        return request;
    }

    private void dismiss(Long reportId, User admin) {
        reportService.applyAction(reportId, dismissAction(), admin.getId());
    }

    private MockHttpServletResponse invoke(RateLimiterFilter filter,
                                           MockHttpServletRequest request,
                                           FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Method method = RateLimiterFilter.class.getDeclaredMethod("doFilterInternal",
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class,
                FilterChain.class);
        method.setAccessible(true);
        method.invoke(filter, request, response, chain);
        return response;
    }
}
