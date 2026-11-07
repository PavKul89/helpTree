package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.reports.AdminReportDetailsResponse;
import org.example.helptreeservice.dto.reports.AdminReportResponse;
import org.example.helptreeservice.dto.reports.AssignReportRequest;
import org.example.helptreeservice.dto.reports.CreateCommentReportRequest;
import org.example.helptreeservice.dto.reports.CreateReportRequest;
import org.example.helptreeservice.dto.reports.ModerationActionRequest;
import org.example.helptreeservice.dto.reports.ModerationActionResponse;
import org.example.helptreeservice.dto.reports.ReportDetailsResponse;
import org.example.helptreeservice.dto.reports.ReportEventResponse;
import org.example.helptreeservice.dto.reports.ReportMetaResponse;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.reports.ResolveReportRequest;
import org.example.helptreeservice.dto.reports.UpdateReportRequest;
import org.example.helptreeservice.entity.Comment;
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
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.CommentRepository;
import org.example.helptreeservice.repository.ModerationActionRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.ReportEventRepository;
import org.example.helptreeservice.repository.ReportRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ownership and lifecycle of a report.
 *
 * <p>The service enforces the product rules documented in
 * {@code docs/REPORTS_MODERATION.md}:</p>
 * <ul>
 *   <li>a user cannot report their own object and cannot keep two open reports
 *       for the same object;</li>
 *   <li>a closed or moderated object is reported as "not found" so its content
 *       is never disclosed;</li>
 *   <li>status changes only happen through {@code /assign} and {@code /actions};
 *       every change is appended to {@code report_events};</li>
 *   <li>a moderation action is stored both as a report event and as an
 *       append-only {@code moderation_actions} row in the same transaction.</li>
 * </ul>
 *
 * <p>Creating a report never blocks, mutes or hides anything. A decision is
 * only made by an administrator through an explicit action.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_INTERNAL_NOTE_LENGTH = 4000;
    private static final int MAX_REASON_LENGTH = 1000;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9._:-]+$");

    private static final List<ReportTargetType> REPORTABLE_TARGETS = List.of(
            ReportTargetType.POST,
            ReportTargetType.USER,
            ReportTargetType.COMMENT,
            ReportTargetType.REVIEW,
            ReportTargetType.MESSAGE);

    private static final List<ReportStatus> OPEN_STATUSES =
            List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW);

    private static final Set<ReportEventType> USER_VISIBLE_EVENT_TYPES =
            EnumSet.complementOf(EnumSet.of(ReportEventType.NOTE));

    private static final Set<ModerationActionType> CONTENT_ACTIONS = EnumSet.of(
            ModerationActionType.HIDE_CONTENT,
            ModerationActionType.REMOVE_CONTENT,
            ModerationActionType.DELETE_CONTENT,
            ModerationActionType.RESTORE_CONTENT);

    private static final Set<ModerationActionType> ACCOUNT_ACTIONS = EnumSet.of(
            ModerationActionType.WARN,
            ModerationActionType.MUTE,
            ModerationActionType.UNMUTE,
            ModerationActionType.BAN,
            ModerationActionType.UNBAN);

    private final ReportRepository reportRepository;
    private final ReportEventRepository reportEventRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ModerationService moderationService;
    private final CommentContentSanitizer contentSanitizer;

    // ============================================================
    // User-facing lifecycle
    // ============================================================

    /**
     * Creates a report. A repeated request with the same {@code Idempotency-Key}
     * returns the already stored report instead of creating a second one.
     */
    @Transactional
    public ReportResponse createReport(CreateReportRequest request, Long userId, String idempotencyKey) {
        User reporter = requireActiveUser(userId);
        String key = normalizeIdempotencyKey(idempotencyKey);

        if (key != null) {
            Report existing = reportRepository.findByReporterIdAndIdempotencyKey(userId, key).orElse(null);
            if (existing != null) {
                log.info("Повторная жалоба по Idempotency-Key {}: возвращена жалоба ID={}",
                        key, existing.getId());
                return toResponse(existing);
            }
        }

        String description = sanitizeDescription(request.getDescription());

        moderationService.requireReportable(request.getTargetType(), request.getTargetId(), userId);

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
                userId, request.getTargetType(), request.getTargetId(), OPEN_STATUSES)) {
            throw new ConflictException("Вы уже пожаловались на этот объект");
        }

        LocalDateTime now = LocalDateTime.now();
        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .description(description)
                .status(ReportStatus.OPEN)
                .priority(ReportPriority.NORMAL)
                .idempotencyKey(key)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Report saved = reportRepository.save(report);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetType", saved.getTargetType().name());
        metadata.put("targetId", saved.getTargetId());
        metadata.put("reason", saved.getReason().name());
        saveEvent(saved, reporter, ReportEventType.CREATED, null, ReportStatus.OPEN, null, metadata);

        log.info("Создана жалоба ID={} на {} ID={} от пользователя ID={}",
                saved.getId(), saved.getTargetType(), saved.getTargetId(), userId);
        return toResponse(saved);
    }

    /**
     * Backwards-compatible wrapper for
     * {@code POST /api/posts/{postId}/comments/{commentId}/reports}.
     */
    @Transactional
    public ReportResponse createCommentReport(Long postId,
                                              Long commentId,
                                              CreateCommentReportRequest request,
                                              Long userId) {
        if (!postRepository.existsByIdAndDeletedFalse(postId)) {
            throw new NotFoundException("Пост не найден");
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий не найден"));
        if (comment.getPost() == null || !comment.getPost().getId().equals(postId)) {
            throw new NotFoundException("Комментарий не найден");
        }

        CreateReportRequest reportRequest = new CreateReportRequest(
                ReportTargetType.COMMENT,
                commentId,
                request.getReason(),
                request.getDetails());
        return createReport(reportRequest, userId, null);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> getMyReports(Long userId,
                                             ReportStatus status,
                                             ReportTargetType targetType,
                                             ReportReason reason,
                                             Pageable pageable) {
        return reportRepository.findMine(userId, status, targetType, reason, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReportDetailsResponse getMyReport(Long reportId, Long userId) {
        Report report = reportRepository.findByIdAndReporterId(reportId, userId)
                .orElseThrow(() -> new NotFoundException("Жалоба не найдена"));

        List<ReportEventResponse> events = reportEventRepository
                .findByReportIdOrderByCreatedAtAscIdAsc(reportId)
                .stream()
                .filter(event -> USER_VISIBLE_EVENT_TYPES.contains(event.getEventType()))
                .map(event -> toEventResponse(event, false))
                .toList();

        return ReportDetailsResponse.builder()
                .report(toResponse(report))
                .events(events)
                .build();
    }

    @Transactional(readOnly = true)
    public ReportMetaResponse getMeta() {
        return ReportMetaResponse.builder()
                .targetTypes(REPORTABLE_TARGETS)
                .reasons(List.of(ReportReason.values()))
                .statuses(List.of(ReportStatus.values()))
                .priorities(List.of(ReportPriority.values()))
                .maxDescriptionLength(MAX_DESCRIPTION_LENGTH)
                .build();
    }

    // ============================================================
    // Administrative queue
    // ============================================================

    @Transactional(readOnly = true)
    public Page<AdminReportResponse> getReports(ReportStatus status,
                                                ReportTargetType targetType,
                                                ReportReason reason,
                                                ReportPriority priority,
                                                Long assignedModeratorId,
                                                Long reporterId,
                                                LocalDateTime from,
                                                LocalDateTime to,
                                                String q,
                                                Pageable pageable) {
        validatePeriod(from, to);
        String query = normalizeQuery(q);
        return reportRepository.findForAdmin(
                        status, targetType, reason, priority, assignedModeratorId,
                        reporterId, from, to, query, pageable)
                .map(this::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public AdminReportDetailsResponse getAdminReport(Long reportId) {
        Report report = reportRepository.findByIdWithDetails(reportId)
                .orElseThrow(() -> new NotFoundException("Жалоба не найдена"));

        return AdminReportDetailsResponse.builder()
                .report(toAdminResponse(report))
                .target(moderationService.preview(report.getTargetType(), report.getTargetId()))
                .events(reportEventRepository.findByReportIdOrderByCreatedAtAscIdAsc(reportId)
                        .stream()
                        .map(event -> toEventResponse(event, true))
                        .toList())
                .actions(moderationActionRepository.findByReportIdOrderByCreatedAtAscIdAsc(reportId)
                        .stream()
                        .map(this::toActionResponse)
                        .toList())
                .build();
    }

    /**
     * Metadata-only update. Status transitions are deliberately rejected here:
     * they must go through {@link #assignReport} or {@link #applyAction} so an
     * audit event always exists.
     */
    @Transactional
    public AdminReportResponse updateReport(Long reportId, UpdateReportRequest request, Long adminId) {
        User admin = requireAdmin(adminId);
        Report report = requireReportForUpdate(reportId);

        if (request.getPriority() == null && request.getInternalNote() == null) {
            throw new BadRequestException("Нет полей для обновления");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        boolean changed = false;

        if (request.getPriority() != null && request.getPriority() != report.getPriority()) {
            metadata.put("previousPriority", report.getPriority().name());
            metadata.put("priority", request.getPriority().name());
            report.setPriority(request.getPriority());
            changed = true;
        }

        String internalNote = null;
        if (request.getInternalNote() != null) {
            internalNote = sanitizeInternalNote(request.getInternalNote());
            if (!java.util.Objects.equals(internalNote, report.getInternalNote())) {
                report.setInternalNote(internalNote);
                metadata.put("internalNoteChanged", true);
                changed = true;
            }
        }

        if (!changed) {
            return toAdminResponse(report);
        }

        LocalDateTime now = LocalDateTime.now();
        report.setUpdatedAt(now);
        reportRepository.save(report);

        saveEvent(report, admin, ReportEventType.NOTE, report.getStatus(), report.getStatus(),
                internalNote, metadata);

        log.info("Жалоба ID={} обновлена администратором ID={}: priority={}, noteChanged={}",
                reportId, adminId, report.getPriority(), internalNote != null);
        return toAdminResponse(report);
    }

    /**
     * Assigns an administrator and moves an open report to {@code IN_REVIEW}.
     * Repeating the same assignment is a no-op, which keeps the endpoint safe
     * for a double click.
     */
    @Transactional
    public AdminReportResponse assignReport(Long reportId, AssignReportRequest request, Long adminId) {
        User admin = requireAdmin(adminId);
        Report report = requireReportForUpdate(reportId);

        if (isClosed(report.getStatus())) {
            throw new ConflictException("Закрытую жалобу нельзя назначить");
        }

        User moderator = admin;
        if (request != null && request.getModeratorId() != null
                && !request.getModeratorId().equals(adminId)) {
            moderator = requireAdminUser(request.getModeratorId());
        }

        boolean sameModerator = report.getAssignedModerator() != null
                && report.getAssignedModerator().getId().equals(moderator.getId());
        if (sameModerator && report.getStatus() == ReportStatus.IN_REVIEW) {
            return toAdminResponse(report);
        }

        ReportStatus from = report.getStatus();
        LocalDateTime now = LocalDateTime.now();
        report.setAssignedModerator(moderator);
        report.setStatus(ReportStatus.IN_REVIEW);
        report.setUpdatedAt(now);
        reportRepository.save(report);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("assignedModeratorId", moderator.getId());
        metadata.put("assignedModeratorName", moderator.getName());
        saveEvent(report, admin, ReportEventType.ASSIGNED, from, ReportStatus.IN_REVIEW, null, metadata);

        log.info("Жалоба ID={} назначена администратору ID={} (было {})",
                reportId, moderator.getId(), from);
        return toAdminResponse(report);
    }

    /**
     * Applies a moderation decision. The report, the action and the history row
     * are written in one transaction; a failure rolls all three back.
     */
    @Transactional
    public ModerationActionResponse applyAction(Long reportId,
                                                ModerationActionRequest request,
                                                Long adminId) {
        User admin = requireAdmin(adminId);
        Report report = requireReportForUpdate(reportId);

        if (isClosed(report.getStatus())) {
            throw new ConflictException("Жалоба уже рассмотрена");
        }

        ModerationActionType actionType = request.getActionType();
        String reason = sanitizeRequiredReason(request.getReason());
        String resolution = sanitizeOptional(request.getResolution(), MAX_REASON_LENGTH);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reportId", report.getId());
        metadata.put("actionType", actionType.name());
        metadata.put("targetType", report.getTargetType().name());
        metadata.put("targetId", report.getTargetId());
        metadata.put("previousReportStatus", report.getStatus().name());

        ReportTargetType actionTargetType;
        Long actionTargetId;
        ReportStatus newStatus;
        ReportEventType eventType;
        String eventNote = null;

        switch (actionType) {
            case DISMISS_REPORT -> {
                newStatus = ReportStatus.REJECTED;
                actionTargetType = ReportTargetType.REPORT;
                actionTargetId = report.getId();
                eventType = ReportEventType.RESOLVED;
                resolution = resolution != null ? resolution : "Жалоба отклонена";
            }
            case LINK_DUPLICATE -> {
                Report original = linkDuplicate(report, request.getDuplicateOfId());
                newStatus = ReportStatus.DUPLICATE;
                actionTargetType = ReportTargetType.REPORT;
                actionTargetId = report.getId();
                eventType = ReportEventType.LINKED_DUPLICATE;
                resolution = resolution != null
                        ? resolution
                        : "Дубликат жалобы #" + original.getId();
                metadata.put("duplicateOfId", original.getId());
            }
            case RESOLVE_REPORT -> {
                newStatus = ReportStatus.RESOLVED;
                actionTargetType = ReportTargetType.REPORT;
                actionTargetId = report.getId();
                eventType = ReportEventType.RESOLVED;
                resolution = resolution != null ? resolution : reason;
            }
            case ESCALATE -> {
                newStatus = ReportStatus.RESOLVED;
                actionTargetType = ReportTargetType.REPORT;
                actionTargetId = report.getId();
                eventType = ReportEventType.ESCALATED;
                resolution = resolution != null ? resolution : reason;
            }
            case OTHER -> {
                newStatus = ReportStatus.RESOLVED;
                actionTargetType = ReportTargetType.REPORT;
                actionTargetId = report.getId();
                eventType = ReportEventType.RESOLVED;
                resolution = resolution != null ? resolution : reason;
            }
            default -> {
                // Content and account actions apply to the reported object.
                actionTargetType = report.getTargetType();
                actionTargetId = report.getTargetId();
                newStatus = ReportStatus.RESOLVED;
                eventType = ReportEventType.RESOLVED;
                resolution = resolution != null ? resolution : reason;
                if (CONTENT_ACTIONS.contains(actionType)) {
                    metadata.putAll(moderationService.applyContentAction(
                            actionType, report.getTargetType(), report.getTargetId(),
                            request.getExpiresAt()));
                } else if (ACCOUNT_ACTIONS.contains(actionType)) {
                    metadata.putAll(moderationService.applyAccountAction(
                            actionType, report.getTargetType(), report.getTargetId(),
                            admin, reason, request.getExpiresAt()));
                } else {
                    throw new BadRequestException("Неподдерживаемое действие модерации");
                }
            }
        }

        if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Время окончания действия должно быть в будущем");
        }

        LocalDateTime now = LocalDateTime.now();
        ReportStatus fromStatus = report.getStatus();
        report.setStatus(newStatus);
        report.setResolution(truncate(resolution, MAX_REASON_LENGTH));
        report.setResolvedBy(admin);
        report.setResolvedAt(now);
        report.setUpdatedAt(now);
        reportRepository.save(report);
        eventNote = resolution;

        ModerationAction action = ModerationAction.builder()
                .report(report)
                .moderator(admin)
                .targetType(actionTargetType)
                .targetId(actionTargetId)
                .actionType(actionType)
                .reason(reason)
                .expiresAt(request.getExpiresAt())
                .metadata(metadata)
                .createdAt(now)
                .build();
        ModerationAction savedAction = moderationActionRepository.save(action);

        metadata.put("moderationActionId", savedAction.getId());
        metadata.put("newReportStatus", newStatus.name());
        saveEvent(report, admin, eventType, fromStatus, newStatus, eventNote, metadata);

        log.info("Жалоба ID={} рассмотрена администратором ID={}: {} -> {}, действие={}",
                reportId, adminId, fromStatus, newStatus, actionType);
        return toActionResponse(savedAction);
    }

    /**
     * Legacy endpoint {@code POST /api/admin/reports/{id}/resolve}. It is kept
     * only for old clients and now delegates to the unified action pipeline.
     */
    @Transactional
    public ReportResponse resolveReport(Long reportId, ResolveReportRequest request, Long adminId) {
        ModerationActionType actionType = request.getAction() == ReportAction.DELETE_COMMENT
                ? ModerationActionType.REMOVE_CONTENT
                : ModerationActionType.DISMISS_REPORT;

        ModerationActionRequest actionRequest = new ModerationActionRequest();
        actionRequest.setActionType(actionType);
        actionRequest.setReason(request.getResolution() == null || request.getResolution().isBlank()
                ? (actionType == ModerationActionType.REMOVE_CONTENT
                        ? "Удалено по жалобе #" + reportId
                        : "Жалоба отклонена")
                : request.getResolution());
        actionRequest.setResolution(request.getResolution());

        applyAction(reportId, actionRequest, adminId);
        Report report = reportRepository.findByIdWithDetails(reportId)
                .orElseThrow(() -> new NotFoundException("Жалоба не найдена"));
        return toResponse(report);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private Report linkDuplicate(Report report, Long duplicateOfId) {
        if (duplicateOfId == null) {
            throw new BadRequestException("Не указана исходная жалоба");
        }
        if (duplicateOfId.equals(report.getId())) {
            throw new BadRequestException("Нельзя связать жалобу саму с собой");
        }

        Report original = reportRepository.findById(duplicateOfId)
                .orElseThrow(() -> new NotFoundException("Исходная жалоба не найдена"));
        if (original.getStatus() == ReportStatus.DUPLICATE) {
            throw new BadRequestException("Исходная жалоба сама является дубликатом");
        }
        if (original.getTargetType() != report.getTargetType()
                || !original.getTargetId().equals(report.getTargetId())) {
            throw new BadRequestException("Связать можно только жалобы на один объект");
        }
        report.setDuplicateOf(original);
        return original;
    }

    private Report requireReportForUpdate(Long reportId) {
        return reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new NotFoundException("Жалоба не найдена"));
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(user.getDeleted()) || user.getAnonymizedAt() != null) {
            throw new NotFoundException("Пользователь не найден");
        }
        if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountBlockedException("Аккаунт заблокирован до " + user.getBlockedUntil());
        }
        return user;
    }

    private User requireAdmin(Long adminId) {
        User admin = requireActiveUser(adminId);
        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Только администратор может рассматривать жалобы");
        }
        return admin;
    }

    private User requireAdminUser(Long userId) {
        User user = requireActiveUser(userId);
        if (user.getRole() != Role.ADMIN) {
            throw new BadRequestException("Назначить можно только администратора");
        }
        return user;
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Начало периода не может быть позже окончания");
        }
    }

    private String normalizeQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%";
    }

    private String normalizeIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String key = rawKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BadRequestException(
                    "Idempotency-Key не может быть длиннее " + MAX_IDEMPOTENCY_KEY_LENGTH + " символов");
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new BadRequestException(
                    "Idempotency-Key может содержать только буквы, цифры, '.', '_', ':' и '-'");
        }
        return key;
    }

    private String sanitizeDescription(String rawDescription) {
        String description = contentSanitizer.sanitize(rawDescription);
        if (description == null || description.isBlank()) {
            return null;
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException(
                    "Описание жалобы не может быть длиннее " + MAX_DESCRIPTION_LENGTH + " символов");
        }
        return description;
    }

    private String sanitizeInternalNote(String rawNote) {
        String note = contentSanitizer.sanitize(rawNote);
        if (note == null || note.isBlank()) {
            return null;
        }
        if (note.length() > MAX_INTERNAL_NOTE_LENGTH) {
            throw new BadRequestException(
                    "Внутренняя заметка не может быть длиннее "
                            + MAX_INTERNAL_NOTE_LENGTH + " символов");
        }
        return note;
    }

    private String sanitizeRequiredReason(String rawReason) {
        String reason = contentSanitizer.sanitize(rawReason);
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Причина действия обязательна");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException(
                    "Причина действия не может быть длиннее " + MAX_REASON_LENGTH + " символов");
        }
        return reason;
    }

    private String sanitizeOptional(String rawValue, int maxLength) {
        String value = contentSanitizer.sanitize(rawValue);
        if (value == null || value.isBlank()) {
            return null;
        }
        return truncate(value, maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean isClosed(ReportStatus status) {
        return status == ReportStatus.RESOLVED
                || status == ReportStatus.REJECTED
                || status == ReportStatus.DUPLICATE;
    }

    private ReportEvent saveEvent(Report report,
                                  User actor,
                                  ReportEventType eventType,
                                  ReportStatus fromStatus,
                                  ReportStatus toStatus,
                                  String internalNote,
                                  Map<String, Object> metadata) {
        ReportEvent event = ReportEvent.builder()
                .report(report)
                .actor(actor)
                .eventType(eventType)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .internalNote(internalNote)
                .metadata(metadata == null ? new LinkedHashMap<>() : metadata)
                .createdAt(LocalDateTime.now())
                .build();
        return reportEventRepository.save(event);
    }

    private ReportResponse toResponse(Report report) {
        User reporter = report.getReporter();
        User assigned = report.getAssignedModerator();
        User resolvedBy = report.getResolvedBy();
        return ReportResponse.builder()
                .id(report.getId())
                .reporterId(reporter == null ? null : reporter.getId())
                .reporterName(reporter == null ? null : reporter.getName())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .description(report.getDescription())
                .status(report.getStatus())
                .priority(report.getPriority())
                .assignedModeratorId(assigned == null ? null : assigned.getId())
                .assignedModeratorName(assigned == null ? null : assigned.getName())
                .resolution(report.getResolution())
                .duplicateOfId(report.getDuplicateOf() == null ? null : report.getDuplicateOf().getId())
                .resolvedById(resolvedBy == null ? null : resolvedBy.getId())
                .resolvedByName(resolvedBy == null ? null : resolvedBy.getName())
                .resolvedAt(report.getResolvedAt())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }

    private AdminReportResponse toAdminResponse(Report report) {
        User reporter = report.getReporter();
        User assigned = report.getAssignedModerator();
        User resolvedBy = report.getResolvedBy();
        return AdminReportResponse.builder()
                .id(report.getId())
                .reporterId(reporter == null ? null : reporter.getId())
                .reporterName(reporter == null ? null : reporter.getName())
                .reporterEmail(reporter == null ? null : reporter.getEmail())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .description(report.getDescription())
                .status(report.getStatus())
                .priority(report.getPriority())
                .assignedModeratorId(assigned == null ? null : assigned.getId())
                .assignedModeratorName(assigned == null ? null : assigned.getName())
                .resolution(report.getResolution())
                .internalNote(report.getInternalNote())
                .duplicateOfId(report.getDuplicateOf() == null ? null : report.getDuplicateOf().getId())
                .resolvedById(resolvedBy == null ? null : resolvedBy.getId())
                .resolvedByName(resolvedBy == null ? null : resolvedBy.getName())
                .resolvedAt(report.getResolvedAt())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }

    private ReportEventResponse toEventResponse(ReportEvent event, boolean admin) {
        User actor = event.getActor();
        return ReportEventResponse.builder()
                .id(event.getId())
                .actorId(admin && actor != null ? actor.getId() : null)
                .actorName(admin && actor != null ? actor.getName() : null)
                .eventType(event.getEventType())
                .fromStatus(event.getFromStatus())
                .toStatus(event.getToStatus())
                .internalNote(admin ? event.getInternalNote() : null)
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private ModerationActionResponse toActionResponse(ModerationAction action) {
        User moderator = action.getModerator();
        return ModerationActionResponse.builder()
                .id(action.getId())
                .reportId(action.getReport() == null ? null : action.getReport().getId())
                .moderatorId(moderator == null ? null : moderator.getId())
                .moderatorName(moderator == null ? null : moderator.getName())
                .actionType(action.getActionType())
                .targetType(action.getTargetType())
                .targetId(action.getTargetId())
                .reason(action.getReason())
                .expiresAt(action.getExpiresAt())
                .metadata(action.getMetadata())
                .createdAt(action.getCreatedAt())
                .build();
    }
}
