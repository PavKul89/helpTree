package org.example.helptreeservice.service;

import org.example.helptreeservice.config.CommentProperties;
import org.example.helptreeservice.dto.reports.AssignReportRequest;
import org.example.helptreeservice.dto.reports.AdminReportResponse;
import org.example.helptreeservice.dto.reports.CreateReportRequest;
import org.example.helptreeservice.dto.reports.ModerationActionRequest;
import org.example.helptreeservice.dto.reports.ModerationActionResponse;
import org.example.helptreeservice.dto.reports.ReportDetailsResponse;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.reports.ResolveReportRequest;
import org.example.helptreeservice.dto.reports.UpdateReportRequest;
import org.example.helptreeservice.entity.ModerationAction;
import org.example.helptreeservice.entity.Report;
import org.example.helptreeservice.entity.ReportEvent;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.ModerationActionType;
import org.example.helptreeservice.enums.ReportAction;
import org.example.helptreeservice.enums.ReportEventType;
import org.example.helptreeservice.enums.ReportPriority;
import org.example.helptreeservice.enums.ReportReason;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.repository.CommentRepository;
import org.example.helptreeservice.repository.ModerationActionRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.ReportEventRepository;
import org.example.helptreeservice.repository.ReportRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ReportEventRepository reportEventRepository;
    @Mock
    private ModerationActionRepository moderationActionRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ModerationService moderationService;

    private ReportService reportService;
    private User reporter;
    private User admin;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(
                reportRepository,
                reportEventRepository,
                moderationActionRepository,
                commentRepository,
                postRepository,
                userRepository,
                moderationService,
                new CommentContentSanitizer(new CommentProperties()));

        reporter = user(1L, "Alice", Role.USER);
        admin = user(3L, "Admin", Role.ADMIN);
    }

    private User user(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@example.com");
        user.setDeleted(false);
        user.setRole(role);
        return user;
    }

    private Report report(Long id, ReportTargetType targetType, Long targetId, ReportStatus status) {
        return Report.builder()
                .id(id)
                .reporter(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .reason(ReportReason.ABUSE)
                .status(status)
                .priority(ReportPriority.NORMAL)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private CreateReportRequest createRequest(ReportTargetType targetType, Long targetId, String description) {
        CreateReportRequest request = new CreateReportRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setReason(ReportReason.ABUSE);
        request.setDescription(description);
        return request;
    }

    private void stubAdminLookup() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
    }

    @Test
    void createReport_shouldStoreSanitizedDescriptionAndCreatedEvent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterIdAndIdempotencyKey(eq(1L), any())).thenReturn(Optional.empty());
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
                eq(1L), eq(ReportTargetType.POST), eq(5L), any())).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        ReportResponse response = reportService.createReport(
                createRequest(ReportTargetType.POST, 5L, "  insult\u0000  "), 1L, "key-1");

        assertEquals(7L, response.getId());
        assertEquals("insult", response.getDescription());
        assertEquals(ReportStatus.OPEN, response.getStatus());
        assertEquals("Alice", response.getReporterName());

        ArgumentCaptor<ReportEvent> eventCaptor = ArgumentCaptor.forClass(ReportEvent.class);
        verify(reportEventRepository).save(eventCaptor.capture());
        assertEquals(ReportEventType.CREATED, eventCaptor.getValue().getEventType());
    }

    @Test
    void createReport_shouldReturnExistingReportForRepeatedIdempotencyKey() {
        Report existing = report(7L, ReportTargetType.POST, 5L, ReportStatus.OPEN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.of(existing));

        ReportResponse response = reportService.createReport(
                createRequest(ReportTargetType.POST, 5L, "text"), 1L, "key-1");

        assertEquals(7L, response.getId());
        verify(reportRepository, never()).save(any(Report.class));
        verify(reportEventRepository, never()).save(any(ReportEvent.class));
    }

    @Test
    void createReport_shouldRejectSecondOpenReportForSameObject() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterIdAndIdempotencyKey(eq(1L), any())).thenReturn(Optional.empty());
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
                eq(1L), eq(ReportTargetType.POST), eq(5L), any())).thenReturn(true);

        assertThrows(ConflictException.class, () -> reportService.createReport(
                createRequest(ReportTargetType.POST, 5L, "text"), 1L, null));
        verify(reportRepository, never()).save(any(Report.class));
    }

    @Test
    void createReport_shouldRejectMalformedIdempotencyKey() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        assertThrows(BadRequestException.class, () -> reportService.createReport(
                createRequest(ReportTargetType.POST, 5L, "text"), 1L, "bad key!"));
    }

    @Test
    void getMyReport_shouldHideInternalNotesAndStaffOnlyEvents() {
        Report report = report(7L, ReportTargetType.POST, 5L, ReportStatus.OPEN);
        when(reportRepository.findByIdAndReporterId(7L, 1L)).thenReturn(Optional.of(report));
        when(reportEventRepository.findByReportIdOrderByCreatedAtAscIdAsc(7L)).thenReturn(List.of(
                ReportEvent.builder()
                        .id(1L)
                        .report(report)
                        .eventType(ReportEventType.NOTE)
                        .internalNote("staff only")
                        .build(),
                ReportEvent.builder()
                        .id(2L)
                        .report(report)
                        .eventType(ReportEventType.CREATED)
                        .build()));

        ReportDetailsResponse details = reportService.getMyReport(7L, 1L);

        assertEquals(1, details.getEvents().size());
        assertEquals(ReportEventType.CREATED, details.getEvents().get(0).getEventType());
        assertNull(details.getEvents().get(0).getInternalNote());
    }

    @Test
    void assignReport_shouldMoveOpenReportToInReviewAndWriteEvent() {
        Report report = report(7L, ReportTargetType.POST, 5L, ReportStatus.OPEN);
        stubAdminLookup();
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminReportResponse response = reportService.assignReport(7L, new AssignReportRequest(), 3L);

        assertEquals(ReportStatus.IN_REVIEW, response.getStatus());
        assertEquals(3L, response.getAssignedModeratorId());
        ArgumentCaptor<ReportEvent> captor = ArgumentCaptor.forClass(ReportEvent.class);
        verify(reportEventRepository).save(captor.capture());
        assertEquals(ReportEventType.ASSIGNED, captor.getValue().getEventType());
        assertEquals(ReportStatus.IN_REVIEW, captor.getValue().getToStatus());
    }

    @Test
    void applyAction_dismissShouldRejectReportWithoutChangingContent() {
        Report report = report(7L, ReportTargetType.COMMENT, 9L, ReportStatus.OPEN);
        stubAdminLookup();
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActionRepository.save(any(ModerationAction.class))).thenAnswer(invocation -> {
            ModerationAction action = invocation.getArgument(0);
            action.setId(11L);
            return action;
        });

        ModerationActionRequest request = new ModerationActionRequest();
        request.setActionType(ModerationActionType.DISMISS_REPORT);
        request.setReason("No violation found");

        ModerationActionResponse response = reportService.applyAction(7L, request, 3L);

        assertEquals(11L, response.getId());
        assertEquals(ModerationActionType.DISMISS_REPORT, response.getActionType());
        assertEquals(ReportStatus.REJECTED, report.getStatus());
        verify(moderationService, never()).applyContentAction(any(), any(), any(), any());
        verify(moderationService, never()).applyAccountAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void applyAction_removeContentShouldChangeModerationStatusAndWriteBothRecords() {
        Report report = report(7L, ReportTargetType.COMMENT, 9L, ReportStatus.IN_REVIEW);
        stubAdminLookup();
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationService.applyContentAction(
                eq(ModerationActionType.REMOVE_CONTENT),
                eq(ReportTargetType.COMMENT), eq(9L), eq(null)))
                .thenReturn(Map.of("moderationStatus", "REMOVED"));
        when(moderationActionRepository.save(any(ModerationAction.class))).thenAnswer(invocation -> {
            ModerationAction action = invocation.getArgument(0);
            action.setId(11L);
            return action;
        });

        ModerationActionRequest request = new ModerationActionRequest();
        request.setActionType(ModerationActionType.REMOVE_CONTENT);
        request.setReason("Abuse");

        ModerationActionResponse response = reportService.applyAction(7L, request, 3L);

        assertEquals(ReportTargetType.COMMENT, response.getTargetType());
        assertEquals(9L, response.getTargetId());
        assertEquals(ReportStatus.RESOLVED, report.getStatus());
        verify(moderationService).applyContentAction(
                ModerationActionType.REMOVE_CONTENT, ReportTargetType.COMMENT, 9L, null);
        verify(reportEventRepository).save(any(ReportEvent.class));
    }

    @Test
    void applyAction_linkDuplicateShouldRejectDifferentTarget() {
        Report report = report(7L, ReportTargetType.COMMENT, 9L, ReportStatus.OPEN);
        Report original = report(8L, ReportTargetType.POST, 5L, ReportStatus.OPEN);
        stubAdminLookup();
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(reportRepository.findById(8L)).thenReturn(Optional.of(original));

        ModerationActionRequest request = new ModerationActionRequest();
        request.setActionType(ModerationActionType.LINK_DUPLICATE);
        request.setReason("Duplicate");
        request.setDuplicateOfId(8L);

        assertThrows(BadRequestException.class, () -> reportService.applyAction(7L, request, 3L));
    }

    @Test
    void applyAction_shouldRejectAlreadyClosedReport() {
        Report report = report(7L, ReportTargetType.COMMENT, 9L, ReportStatus.RESOLVED);
        stubAdminLookup();
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));

        ModerationActionRequest request = new ModerationActionRequest();
        request.setActionType(ModerationActionType.DISMISS_REPORT);
        request.setReason("Late");

        assertThrows(ConflictException.class, () -> reportService.applyAction(7L, request, 3L));
    }

    @Test
    void applyAction_shouldRejectNonAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        ModerationActionRequest request = new ModerationActionRequest();
        request.setActionType(ModerationActionType.DISMISS_REPORT);
        request.setReason("Late");

        assertThrows(ForbiddenException.class, () -> reportService.applyAction(7L, request, 1L));
    }

    @Test
    void updateReport_shouldNotWriteEventWhenNothingChanged() {
        Report report = report(7L, ReportTargetType.POST, 5L, ReportStatus.OPEN);
        report.setPriority(ReportPriority.NORMAL);
        stubAdminLookup();
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));

        UpdateReportRequest request = new UpdateReportRequest();
        request.setPriority(ReportPriority.NORMAL);

        AdminReportResponse response = reportService.updateReport(7L, request, 3L);

        assertEquals(ReportPriority.NORMAL, response.getPriority());
        verify(reportEventRepository, never()).save(any(ReportEvent.class));
    }

    @Test
    void resolveReport_legacyShouldDelegateToUnifiedActionPipeline() {
        Report report = report(7L, ReportTargetType.COMMENT, 9L, ReportStatus.OPEN);
        stubAdminLookup();
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(reportRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(report));
        when(moderationService.applyContentAction(
                eq(ModerationActionType.REMOVE_CONTENT),
                eq(ReportTargetType.COMMENT), eq(9L), eq(null)))
                .thenReturn(Map.of("moderationStatus", "REMOVED"));
        when(moderationActionRepository.save(any(ModerationAction.class))).thenAnswer(invocation -> {
            ModerationAction action = invocation.getArgument(0);
            action.setId(11L);
            return action;
        });

        ResolveReportRequest request = new ResolveReportRequest();
        request.setAction(ReportAction.DELETE_COMMENT);
        request.setResolution("Abuse");

        ReportResponse response = reportService.resolveReport(7L, request, 3L);

        assertEquals(ReportStatus.RESOLVED, response.getStatus());
        verify(moderationService).applyContentAction(
                ModerationActionType.REMOVE_CONTENT, ReportTargetType.COMMENT, 9L, null);
        assertTrue(response.getResolution().contains("Abuse"));
    }
}
