package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.reports.CreateCommentReportRequest;
import org.example.helptreeservice.dto.reports.ReportResponse;
import org.example.helptreeservice.dto.reports.ResolveReportRequest;
import org.example.helptreeservice.entity.Comment;
import org.example.helptreeservice.entity.Report;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.ReportAction;
import org.example.helptreeservice.enums.ReportPriority;
import org.example.helptreeservice.enums.ReportStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.CommentRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.ReportRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int MAX_DETAILS_LENGTH = 2000;
    private static final List<ReportStatus> OPEN_STATUSES =
            List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW);

    private final ReportRepository reportRepository;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentService commentService;
    private final CommentContentSanitizer contentSanitizer;

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
        if (Boolean.TRUE.equals(comment.getDeleted())) {
            throw new BadRequestException("Нельзя пожаловаться на удалённый комментарий");
        }

        User reporter = requireActiveUser(userId);
        if (comment.getUser() != null && comment.getUser().getId().equals(userId)) {
            throw new BadRequestException("Нельзя пожаловаться на свой комментарий");
        }

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
                userId, ReportTargetType.COMMENT, commentId, OPEN_STATUSES)) {
            throw new ConflictException("Вы уже пожаловались на этот комментарий");
        }

        String details = contentSanitizer.sanitize(request.getDetails());
        if (details != null && details.length() > MAX_DETAILS_LENGTH) {
            throw new BadRequestException(
                    "Детали жалобы не могут быть длиннее " + MAX_DETAILS_LENGTH + " символов");
        }

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(ReportTargetType.COMMENT)
                .targetId(commentId)
                .reason(request.getReason())
                .details(details)
                .status(ReportStatus.OPEN)
                .priority(ReportPriority.NORMAL)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Report saved = reportRepository.save(report);
        log.info("Создана жалоба ID={} на комментарий ID={} от пользователя ID={}",
                saved.getId(), commentId, userId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> getReports(ReportStatus status,
                                           ReportTargetType targetType,
                                           Pageable pageable) {
        return reportRepository.findForAdmin(status, targetType, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public ReportResponse resolveReport(Long reportId, ResolveReportRequest request, Long adminId) {
        User admin = requireActiveUser(adminId);
        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Только администратор может рассматривать жалобы");
        }

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Жалоба не найдена"));
        if (isClosed(report.getStatus())) {
            throw new ConflictException("Жалоба уже рассмотрена");
        }

        String resolution = request.getResolution() == null ? null : request.getResolution().strip();
        if (request.getAction() == ReportAction.DELETE_COMMENT) {
            if (report.getTargetType() != ReportTargetType.COMMENT) {
                throw new BadRequestException("Удаление контента поддерживается только для комментариев");
            }
            commentService.moderateDeleteComment(report.getTargetId(), adminId,
                    resolution == null || resolution.isBlank()
                            ? "Удалено по жалобе #" + report.getId()
                            : resolution);
            report.setStatus(ReportStatus.RESOLVED);
            report.setResolution(resolution == null || resolution.isBlank()
                    ? "Комментарий удалён модератором"
                    : resolution);
        } else {
            report.setStatus(ReportStatus.REJECTED);
            report.setResolution(resolution == null || resolution.isBlank()
                    ? "Жалоба отклонена"
                    : resolution);
        }

        LocalDateTime now = LocalDateTime.now();
        report.setResolvedBy(admin);
        report.setResolvedAt(now);
        report.setUpdatedAt(now);
        reportRepository.save(report);

        closeDuplicateReports(report, admin, now);

        log.info("Жалоба ID={} рассмотрена администратором ID={} со статусом {}",
                reportId, adminId, report.getStatus());
        return toResponse(report);
    }

    private void closeDuplicateReports(Report resolved, User admin, LocalDateTime now) {
        List<Report> duplicates = reportRepository.findByTargetTypeAndTargetIdAndStatusIn(
                        resolved.getTargetType(), resolved.getTargetId(), OPEN_STATUSES)
                .stream()
                .filter(report -> !report.getId().equals(resolved.getId()))
                .toList();
        for (Report duplicate : duplicates) {
            duplicate.setStatus(ReportStatus.DUPLICATE);
            duplicate.setResolution("Дубликат жалобы #" + resolved.getId());
            duplicate.setResolvedBy(admin);
            duplicate.setResolvedAt(now);
            duplicate.setUpdatedAt(now);
        }
        if (!duplicates.isEmpty()) {
            reportRepository.saveAll(duplicates);
        }
    }

    private boolean isClosed(ReportStatus status) {
        return status == ReportStatus.RESOLVED
                || status == ReportStatus.REJECTED
                || status == ReportStatus.DUPLICATE;
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }
        if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountBlockedException("Аккаунт заблокирован до " + user.getBlockedUntil());
        }
        return user;
    }

    private ReportResponse toResponse(Report report) {
        User reporter = report.getReporter();
        User resolvedBy = report.getResolvedBy();
        return ReportResponse.builder()
                .id(report.getId())
                .reporterId(reporter == null ? null : reporter.getId())
                .reporterName(reporter == null ? null : reporter.getName())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .details(report.getDetails())
                .status(report.getStatus())
                .priority(report.getPriority())
                .resolution(report.getResolution())
                .resolvedById(resolvedBy == null ? null : resolvedBy.getId())
                .resolvedByName(resolvedBy == null ? null : resolvedBy.getName())
                .resolvedAt(report.getResolvedAt())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
