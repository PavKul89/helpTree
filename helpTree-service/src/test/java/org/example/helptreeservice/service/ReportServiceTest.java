package org.example.helptreeservice.service;

import org.example.helptreeservice.config.CommentProperties;
import org.example.helptreeservice.dto.comments.CommentActionResponse;
import org.example.helptreeservice.dto.reports.CreateCommentReportRequest;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.reports.ResolveReportRequest;
import org.example.helptreeservice.entity.Comment;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.Report;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.ReportAction;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.repository.CommentRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.ReportRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CommentService commentService;

    private ReportService reportService;
    private Post post;
    private User author;
    private User reporter;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(
                reportRepository,
                commentRepository,
                postRepository,
                userRepository,
                commentService,
                new CommentContentSanitizer(new CommentProperties()));

        author = user(2L, "Bob", Role.USER);
        reporter = user(1L, "Alice", Role.USER);
        post = new Post();
        post.setId(5L);
        post.setDeleted(false);
        post.setUser(author);
    }

    private User user(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setDeleted(false);
        user.setRole(role);
        return user;
    }

    private Comment comment(Long id, User commentAuthor) {
        return Comment.builder()
                .id(id)
                .post(post)
                .user(commentAuthor)
                .content("bad comment")
                .depth(0)
                .deleted(false)
                .deletedByAuthor(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private CreateCommentReportRequest reportRequest() {
        CreateCommentReportRequest request = new CreateCommentReportRequest();
        request.setReason(ReportReason.ABUSE);
        request.setDetails("  insult\u0000  ");
        return request;
    }

    private ResolveReportRequest resolveRequest(ReportAction action, String resolution) {
        ResolveReportRequest request = new ResolveReportRequest();
        request.setAction(action);
        request.setResolution(resolution);
        return request;
    }

    @Test
    void createCommentReport_shouldStoreReportWithSanitizedDetails() {
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(true);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment(9L, author)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
                eq(1L), eq(ReportTargetType.COMMENT), eq(9L), any())).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setId(3L);
            return report;
        });

        ReportResponse response = reportService.createCommentReport(5L, 9L, reportRequest(), 1L);

        assertEquals(3L, response.getId());
        assertEquals(ReportTargetType.COMMENT, response.getTargetType());
        assertEquals(9L, response.getTargetId());
        assertEquals(ReportReason.ABUSE, response.getReason());
        assertEquals("insult", response.getDetails());
        assertEquals(ReportStatus.OPEN, response.getStatus());
        assertEquals("Alice", response.getReporterName());
    }

    @Test
    void createCommentReport_shouldRejectOwnComment() {
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(true);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment(9L, reporter)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        assertThrows(BadRequestException.class,
                () -> reportService.createCommentReport(5L, 9L, reportRequest(), 1L));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void createCommentReport_shouldRejectDeletedComment() {
        Comment deleted = comment(9L, author);
        deleted.setDeleted(true);
        deleted.setDeletedAt(LocalDateTime.now());
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(true);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(deleted));

        assertThrows(BadRequestException.class,
                () -> reportService.createCommentReport(5L, 9L, reportRequest(), 1L));
    }

    @Test
    void createCommentReport_shouldRejectDuplicateOpenReport() {
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(true);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment(9L, author)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
                eq(1L), eq(ReportTargetType.COMMENT), eq(9L), any())).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> reportService.createCommentReport(5L, 9L, reportRequest(), 1L));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void resolveReport_shouldDeleteCommentAndCloseDuplicates() {
        User admin = user(3L, "Admin", Role.ADMIN);
        Report report = Report.builder()
                .id(7L)
                .reporter(reporter)
                .targetType(ReportTargetType.COMMENT)
                .targetId(9L)
                .reason(ReportReason.ABUSE)
                .status(ReportStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Report duplicate = Report.builder()
                .id(8L)
                .reporter(reporter)
                .targetType(ReportTargetType.COMMENT)
                .targetId(9L)
                .reason(ReportReason.SPAM)
                .status(ReportStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatusIn(
                ReportTargetType.COMMENT, 9L, List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW)))
                .thenReturn(List.of(report, duplicate));
        when(commentService.moderateDeleteComment(eq(9L), eq(3L), any()))
                .thenReturn(CommentActionResponse.builder().totalComments(0).build());

        ReportResponse response = reportService.resolveReport(
                7L, resolveRequest(ReportAction.DELETE_COMMENT, "Нарушение правил"), 3L);

        assertEquals(ReportStatus.RESOLVED, response.getStatus());
        assertEquals("Нарушение правил", response.getResolution());
        assertEquals(3L, response.getResolvedById());
        assertEquals(ReportStatus.DUPLICATE, duplicate.getStatus());
        assertNotNull(duplicate.getResolvedAt());
        verify(commentService).moderateDeleteComment(9L, 3L, "Нарушение правил");
        verify(reportRepository).saveAll(List.of(duplicate));
    }

    @Test
    void resolveReport_shouldRejectReportWithoutDeletingComment() {
        User admin = user(3L, "Admin", Role.ADMIN);
        Report report = Report.builder()
                .id(7L)
                .reporter(reporter)
                .targetType(ReportTargetType.COMMENT)
                .targetId(9L)
                .reason(ReportReason.SPAM)
                .status(ReportStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatusIn(
                ReportTargetType.COMMENT, 9L, List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW)))
                .thenReturn(List.of(report));

        ReportResponse response = reportService.resolveReport(
                7L, resolveRequest(ReportAction.REJECT, null), 3L);

        assertEquals(ReportStatus.REJECTED, response.getStatus());
        assertEquals("Жалоба отклонена", response.getResolution());
        verify(commentService, never()).moderateDeleteComment(any(), any(), any());
    }

    @Test
    void resolveReport_shouldRejectNonAdmin() {
        User user = user(1L, "Alice", Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class,
                () -> reportService.resolveReport(7L, resolveRequest(ReportAction.REJECT, null), 1L));
    }

    @Test
    void resolveReport_shouldRejectAlreadyClosedReport() {
        User admin = user(3L, "Admin", Role.ADMIN);
        Report report = Report.builder()
                .id(7L)
                .targetType(ReportTargetType.COMMENT)
                .targetId(9L)
                .status(ReportStatus.RESOLVED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));

        assertThrows(ConflictException.class,
                () -> reportService.resolveReport(7L, resolveRequest(ReportAction.REJECT, null), 3L));
    }
}
