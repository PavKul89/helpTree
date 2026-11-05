package org.example.helptreeservice.service;


import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.dto.helps.HelpHistoryEntryDto;
import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.HelpStatusHistory;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpActorRole;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.HelpMapper;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.HelpStatusHistoryRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.repository.projection.ConfirmedHelpGraphProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class HelpService {

    private final HelpRepository helpRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final HelpMapper helpMapper;
    private final RatingService ratingService;
    private final AchievementService achievementService;
    private final WalletService walletService;
    private final ImageService imageService;
    private final EconomyConfig economyConfig;
    private final HelpStatusHistoryRepository statusHistoryRepository;
    private final HelpGraphMetrics helpGraphMetrics;

    private static final List<HelpStatus> ACTIVE_HELP_STATUSES =
            List.of(HelpStatus.ACCEPTED, HelpStatus.COMPLETED);
    private static final int MAX_SLA_BATCH_SIZE = 500;

    /**
     * 1. Помощник откликается на пост.
     *
     * Блокировка строки поста сериализует конкурентные отклики: только один
     * помощник увидит пост в статусе OPEN и создаст активную помощь. Частичный
     * уникальный индекс uq_helps_active_post служит вторым рубежом.
     */
    public HelpResponse acceptHelp(HelpRequest request, Long helperId) {
        log.info("Принятие помощи: helperId={} откликается на postId={}",
                helperId, request.getPostId());

        Post post = postRepository.findByIdForUpdate(request.getPostId())
                .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + request.getPostId()));

        if (Boolean.TRUE.equals(post.getDeleted())) {
            throw new NotFoundException("Пост не найден");
        }
        if (post.getStatus() != PostStatus.OPEN) {
            throw new ConflictException("Заявка уже не открыта для откликов");
        }
        if (post.getHelper() != null) {
            throw new ConflictException("На этот пост уже кто-то откликнулся");
        }

        User helper = userRepository.findById(helperId)
                .orElseThrow(() -> new NotFoundException("Помощник не найден с id: " + helperId));
        if (Boolean.TRUE.equals(helper.getDeleted())) {
            throw new NotFoundException("Помощник не найден");
        }

        User receiver = post.getUser();
        if (receiver == null || Boolean.TRUE.equals(receiver.getDeleted())) {
            throw new NotFoundException("Автор заявки не найден");
        }

        LocalDateTime now = LocalDateTime.now();

        if (helper.getId().equals(receiver.getId())) {
            throw new BadRequestException("Нельзя помочь самому себе");
        }

        // Помощь — способ выйти из долга, поэтому для помощника ограничением
        // является именно активная блокировка, а не размер долга.
        if (isActivelyBlocked(helper, now)) {
            throw new ForbiddenException("Ваш аккаунт заблокирован за долг. Помогите другим пользователям, чтобы разблокировать аккаунт.");
        }
        if (isActivelyBlocked(receiver, now)) {
            throw new BadRequestException("Автор поста заблокирован за долг. Нельзя откликнуться на его пост.");
        }
        checkReceiverDebt(receiver);

        if (helpRepository.existsByPostAndHelper(post, helper)) {
            throw new ConflictException("Вы уже откликались на этот пост");
        }
        if (helpRepository.existsByPostIdAndStatusIn(post.getId(), ACTIVE_HELP_STATUSES)) {
            throw new ConflictException("На этот пост уже кто-то откликнулся");
        }

        Help help = new Help();
        help.setPost(post);
        help.setHelper(helper);
        help.setReceiver(receiver);
        help.setStatus(HelpStatus.ACCEPTED);
        help.setAcceptedAt(now);
        help.setCreatedAt(now);
        help.setUpdatedAt(now);

        Help savedHelp;
        try {
            savedHelp = helpRepository.saveAndFlush(help);
        } catch (DataIntegrityViolationException e) {
            log.warn("Конкурентный отклик отклонён уникальным индексом: postId={}, helperId={}",
                    post.getId(), helper.getId());
            throw new ConflictException("На этот пост уже кто-то откликнулся");
        }

        post.setStatus(PostStatus.IN_PROGRESS);
        post.setHelper(helper);
        post.setUpdatedAt(now);
        postRepository.save(post);

        recordHistory(savedHelp, null, HelpStatus.ACCEPTED, helperId,
                HelpActorRole.HELPER, null, now);

        // The new ACCEPTED record changes the user's help projection
        // (totalHelpRecords / activeHelps). Rebuild it from the flushed fact so
        // the rating response never reports a stale active-help count.
        ratingService.recalculateUsers(List.of(helperId));

        log.info("Помощь успешно принята: helpId={}, helperId={}, receiverId={}, postId={}",
                savedHelp.getId(), helper.getId(), receiver.getId(), post.getId());

        return helpMapper.toResponse(savedHelp);
    }

    /**
     * 2. Помощник отмечает, что помощь выполнена.
     *
     * Это только переход ACCEPTED -> COMPLETED. Награды и подтверждённая
     * статистика фиксируются позже, ровно один раз, в confirmHelp. Проекция
     * помощи обновляется сразу, потому что запись остаётся активной.
     */
    public HelpResponse completeHelp(Long helpId, Long actorId, boolean admin) {
        log.info("Завершение помощи: helpId={}, actorId={}, admin={}", helpId, actorId, admin);

        Help help = getHelpByIdForUpdate(helpId);
        Long helperId = help.getHelper().getId();

        if (!admin && !helperId.equals(actorId)) {
            throw new ForbiddenException("Завершить помощь может только помощник");
        }

        if (help.getStatus() == HelpStatus.CANCELLED) {
            throw new ConflictException("Нельзя завершить отменённую помощь");
        }
        if (help.getStatus() == HelpStatus.CONFIRMED) {
            throw new ConflictException("Помощь уже подтверждена");
        }
        if (help.getStatus() == HelpStatus.COMPLETED) {
            // Идемпотентный повтор: возвращаем актуальное состояние без наград.
            return helpMapper.toResponse(help);
        }
        if (help.getStatus() != HelpStatus.ACCEPTED) {
            throw new ConflictException("Помощь не в статусе ACCEPTED");
        }

        LocalDateTime now = LocalDateTime.now();
        help.setStatus(HelpStatus.COMPLETED);
        help.setCompletedAt(now);
        help.setUpdatedAt(now);
        Help updatedHelp = helpRepository.saveAndFlush(help);

        recordHistory(updatedHelp, HelpStatus.ACCEPTED, HelpStatus.COMPLETED, actorId,
                admin ? HelpActorRole.ADMIN : HelpActorRole.HELPER, null, now);

        // ACCEPTED and COMPLETED are both active help records; the projection
        // stores their count separately from confirmed acts.
        ratingService.recalculateUsers(List.of(helperId));

        log.info("Помощь отмечена как выполненная: helpId={}", helpId);
        return helpMapper.toResponse(updatedHelp);
    }

    /**
     * 3. Получатель подтверждает, что помощь получена.
     *
     * Единственная точка фиксации успешной помощи: финальный статус, статус
     * заявки, статистика, монеты, достижения и история обновляются здесь и
     * только при переходе COMPLETED -> CONFIRMED под блокировкой строки помощи.
     * Повторный вызов возвращает текущее состояние и ничего не начисляет.
     */
    public HelpResponse confirmHelp(Long helpId, Long actorId, boolean admin) {
        log.info("Подтверждение получения помощи: helpId={}, actorId={}, admin={}",
                helpId, actorId, admin);

        Help help = getHelpByIdForUpdate(helpId);
        Long helperId = help.getHelper().getId();
        Long receiverId = help.getReceiver().getId();

        if (!admin && !receiverId.equals(actorId)) {
            throw new ForbiddenException("Подтвердить помощь может только получатель");
        }

        if (help.getStatus() == HelpStatus.CANCELLED) {
            throw new ConflictException("Нельзя подтвердить отменённую помощь");
        }
        if (help.getStatus() == HelpStatus.CONFIRMED) {
            // Идемпотентный повтор: награды уже начислены, повторно не начисляем.
            return helpMapper.toResponse(help);
        }
        if (help.getStatus() != HelpStatus.COMPLETED) {
            throw new ConflictException("Подтвердить можно только помощь, отмеченную как выполненная");
        }

        LocalDateTime now = LocalDateTime.now();

        // Блокируем обоих участников в детерминированном порядке: балансы и
        // счётчики долга не должны потерять параллельные изменения.
        List<Long> userIds = helperId < receiverId
                ? List.of(helperId, receiverId)
                : List.of(receiverId, helperId);
        List<User> lockedUsers = userRepository.findAllByIdInForUpdate(userIds);
        User receiver = lockedUsers.stream()
                .filter(u -> u.getId().equals(receiverId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Получатель не найден"));

        if (isActivelyBlocked(receiver, now)) {
            throw new BadRequestException("Нельзя подтвердить помощь заблокированному пользователю");
        }

        help.setStatus(HelpStatus.CONFIRMED);
        help.setConfirmedAt(now);
        help.setUpdatedAt(now);
        helpRepository.saveAndFlush(help);

        Post post = help.getPost();
        post.setStatus(PostStatus.COMPLETED);
        post.setUpdatedAt(now);
        postRepository.save(post);

        userService.incrementHelpedCount(receiverId);
        userService.userHelpedSomeone(helperId);

        walletService.addCoinsForHelp(helperId, receiverId, post.getId());
        walletService.addCoinsForReceivedHelp(receiverId, helperId, post.getId());
        achievementService.checkAndAwardAchievements(help.getHelper(), help);
        // Projection rebuild from the just-flushed CONFIRMED fact. Repeating a
        // confirmation cannot double-count because nothing is incremented.
        ratingService.recalculateUsers(List.of(helperId, receiverId));

        recordHistory(help, HelpStatus.COMPLETED, HelpStatus.CONFIRMED, actorId,
                admin ? HelpActorRole.ADMIN : HelpActorRole.RECEIVER, null, now);

        log.info("Помощь успешно подтверждена: helpId={}", helpId);
        return helpMapper.toResponse(help);
    }

    /**
     * 4. Отмена помощи по утверждённой политике.
     *
     * Помощник: ACCEPTED или COMPLETED. Получатель: только ACCEPTED.
     * ADMIN: любой не-CONFIRMED статус, обязательно с причиной.
     * SYSTEM: SLA-отмена зависших ACCEPTED.
     */
    public HelpResponse cancelHelp(Long helpId, Long actorId, boolean admin, String reason) {
        log.info("Отмена помощи: helpId={}, actorId={}, admin={}", helpId, actorId, admin);

        Help help = getHelpByIdForUpdate(helpId);
        Long helperId = help.getHelper().getId();
        Long receiverId = help.getReceiver().getId();

        HelpActorRole initiator;
        if (admin) {
            initiator = HelpActorRole.ADMIN;
        } else if (helperId.equals(actorId)) {
            initiator = HelpActorRole.HELPER;
        } else if (receiverId.equals(actorId)) {
            initiator = HelpActorRole.RECEIVER;
        } else {
            throw new ForbiddenException("Вы не являетесь участником этой помощи");
        }

        if (help.getStatus() == HelpStatus.CANCELLED) {
            // Идемпотентный повтор: состояние уже актуально, повторных записей нет.
            return helpMapper.toResponse(help);
        }
        if (help.getStatus() == HelpStatus.CONFIRMED) {
            throw new ConflictException("Нельзя отменить подтверждённую помощь");
        }

        if (!admin) {
            if (initiator == HelpActorRole.RECEIVER && help.getStatus() != HelpStatus.ACCEPTED) {
                throw new ForbiddenException("Получатель может отменить помощь только до отметки выполнения");
            }
            if (initiator == HelpActorRole.HELPER
                    && help.getStatus() != HelpStatus.ACCEPTED
                    && help.getStatus() != HelpStatus.COMPLETED) {
                throw new ConflictException("Помощь нельзя отменить в текущем статусе");
            }
        }

        String normalizedReason = normalizeReason(reason);
        if (initiator == HelpActorRole.ADMIN && normalizedReason == null) {
            throw new BadRequestException("Администратор должен указать причину отмены");
        }

        applyCancellation(help, initiator, actorId, normalizedReason);
        log.info("Помощь отменена: helpId={}, initiator={}", helpId, initiator);
        return helpMapper.toResponse(help);
    }

    @Transactional(readOnly = true)
    public List<HelpHistoryEntryDto> getHelpHistory(Long helpId, Long actorId, boolean admin) {
        Help help = getHelpById(helpId);
        boolean participant = actorId != null
                && (actorId.equals(help.getHelper().getId()) || actorId.equals(help.getReceiver().getId()));
        if (!admin && !participant) {
            throw new ForbiddenException("История доступна только участникам помощи");
        }

        return statusHistoryRepository.findByHelpIdOrderByCreatedAtAsc(helpId).stream()
                .map(entry -> HelpHistoryEntryDto.builder()
                        .id(entry.getId())
                        .fromStatus(entry.getFromStatus() != null ? entry.getFromStatus().name() : null)
                        .toStatus(entry.getToStatus().name())
                        .actorId(entry.getActorId())
                        .actorRole(entry.getActorRole().name())
                        .reason(entry.getReason())
                        .createdAt(entry.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> findStaleAcceptedHelpIds(LocalDateTime cutoff, int limit) {
        int batchSize = Math.max(1, Math.min(limit, MAX_SLA_BATCH_SIZE));
        return helpRepository.findIdsByStatusAndAcceptedAtBefore(
                HelpStatus.ACCEPTED, cutoff, PageRequest.of(0, batchSize));
    }

    /**
     * Per-help transaction invoked by the SLA scheduler through the Spring proxy.
     * Rechecks the status under a row lock so a help completed at the same time
     * as the sweep is never cancelled.
     */
    public boolean cancelStaleAcceptedHelp(Long helpId, LocalDateTime cutoff) {
        Help help = helpRepository.findByIdForUpdate(helpId).orElse(null);
        if (help == null || Boolean.TRUE.equals(help.getDeleted())) {
            return false;
        }
        if (help.getStatus() != HelpStatus.ACCEPTED) {
            return false;
        }
        if (help.getAcceptedAt() == null || !help.getAcceptedAt().isBefore(cutoff)) {
            return false;
        }

        String reason = "Автоматическая отмена: помощь не завершена в установленный срок";
        applyCancellation(help, HelpActorRole.SYSTEM, null, reason);
        log.info("SLA: помощь автоматически отменена helpId={}", helpId);
        return true;
    }

    private void applyCancellation(Help help, HelpActorRole initiator, Long actorId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        HelpStatus fromStatus = help.getStatus();
        Long helperId = help.getHelper().getId();

        help.setStatus(HelpStatus.CANCELLED);
        help.setCancelledAt(now);
        help.setCancelledById(initiator == HelpActorRole.SYSTEM ? null : actorId);
        help.setCancelInitiator(initiator);
        help.setCancelReason(reason != null ? reason : defaultCancelReason(initiator));
        help.setUpdatedAt(now);

        Post post = help.getPost();
        if (post != null && post.getStatus() == PostStatus.IN_PROGRESS) {
            boolean ownsHelper = post.getHelper() == null
                    || post.getHelper().getId().equals(helperId);
            if (ownsHelper) {
                post.setStatus(PostStatus.OPEN);
                post.setHelper(null);
                post.setUpdatedAt(now);
                postRepository.save(post);
            }
        }

        recordHistory(help, fromStatus, HelpStatus.CANCELLED, actorId, initiator,
                help.getCancelReason(), now);

        helpRepository.saveAndFlush(help);
        // The cancellation is a fact now; rebuild the helper's projection.
        ratingService.recalculateUsers(List.of(helperId));
    }

    private void recordHistory(Help help, HelpStatus fromStatus, HelpStatus toStatus,
                               Long actorId, HelpActorRole actorRole, String reason,
                               LocalDateTime createdAt) {
        HelpStatusHistory history = new HelpStatusHistory();
        history.setHelp(help);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setActorId(actorId);
        history.setActorRole(actorRole);
        history.setReason(reason);
        history.setCreatedAt(createdAt);
        statusHistoryRepository.save(history);
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultCancelReason(HelpActorRole initiator) {
        return switch (initiator) {
            case HELPER -> "Отменено помощником";
            case RECEIVER -> "Отменено получателем";
            case ADMIN -> "Отменено администратором";
            case SYSTEM -> "Автоматическая отмена системой";
        };
    }

    private boolean isActivelyBlocked(User user, LocalDateTime now) {
        if (user.getBlockedUntil() != null) {
            return user.getBlockedUntil().isAfter(now);
        }
        return user.getBlockedAt() != null;
    }

    private void checkReceiverDebt(User receiver) {
        int debt = receiver.getDebtCount() != null ? receiver.getDebtCount() : 0;
        int helped = receiver.getHelpedCount() != null ? receiver.getHelpedCount() : 0;
        if (debt > economyConfig.getDebtBlockThreshold() && helped < debt) {
            throw new BadRequestException("Пользователь заблокирован за долг. Невозможно откликнуться на пост.");
        }
    }

    /**
     * Получить все помощи пользователя (где он помогал)
     */
    @Transactional(readOnly = true)
    public Page<HelpResponse> getHelpsByPost(Long postId, Pageable pageable) {
        log.info("Запрос откликов по посту ID: {}, page={}, size={}", postId, pageable.getPageNumber(), pageable.getPageSize());
        return helpRepository.findByPostId(postId, pageable)
                .map(helpMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<HelpResponse> getHelpsByHelper(Long helperId, Pageable pageable) {
        log.info("Запрос откликов помощника с ID: {}, page={}, size={}", helperId, pageable.getPageNumber(), pageable.getPageSize());

        try {
            User helper = userRepository.findById(helperId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + helperId));

            if (helper.getDeleted() != null && helper.getDeleted()) {
                log.warn("Попытка получить помощи удаленного пользователя: helperId={}", helperId);
                throw new NotFoundException("Пользователь не найден");
            }

            Page<HelpResponse> helps = helpRepository.findByHelperWithDetails(helper, pageable)
                    .map(helpMapper::toResponse);

            log.info("Найдено {} откликов для помощника с ID: {}", helps.getTotalElements(), helperId);

            return helps;

        } catch (NotFoundException e) {
            log.warn("Не удалось получить отклики помощника: helperId={} не найден", helperId);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при получении откликов помощника с ID: {}", helperId, e);
            throw e;
        }
    }

    /**
     * Получить все помощи пользователя (где ему помогали)
     */
    @Transactional(readOnly = true)
    public Page<HelpResponse> getHelpsByReceiver(Long receiverId, Pageable pageable) {
        log.info("Запрос полученных помощью пользователя с ID: {}, page={}, size={}", receiverId, pageable.getPageNumber(), pageable.getPageSize());

        try {
            User receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + receiverId));

            if (receiver.getDeleted() != null && receiver.getDeleted()) {
                log.warn("Попытка получить помощи удаленного пользователя: receiverId={}", receiverId);
                throw new NotFoundException("Пользователь не найден");
            }

            Page<HelpResponse> helps = helpRepository.findByReceiverWithDetails(receiver, pageable)
                    .map(helpMapper::toResponse);

            log.info("Найдено {} полученных помощью для пользователя с ID: {}", helps.getTotalElements(), receiverId);

            return helps;

        } catch (NotFoundException e) {
            log.warn("Не удалось получить полученные помощи: receiverId={} не найден", receiverId);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при получении полученных помощью пользователя с ID: {}", receiverId, e);
            throw e;
        }
    }

    /**
     * Получить количество новых ответов на посты пользователя (от последнего входа)
     */
    @Transactional(readOnly = true)
    public long getNewResponsesCount(Long userId, String sinceParam) {
        log.info("Подсчет новых ответов для userId: {}", userId);

        LocalDateTime since;
        if (sinceParam != null && !sinceParam.isEmpty()) {
            since = LocalDateTime.parse(sinceParam);
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + userId));
            since = user.getLastLogin();
            if (since == null) {
                since = LocalDateTime.of(1970, 1, 1, 0, 0);
            }
        }

        long count = helpRepository.countNewResponsesSince(userId, since);
        log.info("Найдено {} новых ответов для userId: {}", count, userId);
        return count;
    }

    @Transactional(readOnly = true)
    public List<org.example.helptreeservice.dto.helps.NewResponseDto> getNewResponses(Long userId, String sinceParam) {
        log.info("Получение ответов для userId: {}", userId);

        List<Object[]> rows = helpRepository.findNewResponsesData(userId);
        List<org.example.helptreeservice.dto.helps.NewResponseDto> responses = new ArrayList<>();

        for (Object[] row : rows) {
            org.example.helptreeservice.dto.helps.NewResponseDto dto = new org.example.helptreeservice.dto.helps.NewResponseDto();
            dto.setHelpId(((Number) row[0]).longValue());
            dto.setPostId(((Number) row[1]).longValue());
            dto.setPostTitle((String) row[2]);
            dto.setHelperName(row[3] != null ? row[3].toString() : "Неизвестно");
            if (row[4] != null) {
                dto.setCreatedAt(row[4].toString());
            }
            responses.add(dto);
        }

        log.info("Найдено {} ответов для userId: {}", responses.size(), userId);
        return responses;
    }

    /**
     * Получить помощь по ID
     */
    public Help getHelpById(Long id) {
        log.debug("Поиск помощи по ID: {}", id);

        Help help = helpRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Помощь не найдена с id: " + id));

        if (help.getDeleted() != null && help.getDeleted()) {
            log.debug("Помощь с ID {} найдена, но помечена как удаленная", id);
            throw new NotFoundException("Помощь не найдена с id: " + id);
        }

        log.debug("Помощь с ID {} успешно найдена", id);
        return help;
    }

    private Help getHelpByIdForUpdate(Long id) {
        log.debug("Поиск помощи по ID с блокировкой: {}", id);

        Help help = helpRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Помощь не найдена с id: " + id));

        if (help.getDeleted() != null && help.getDeleted()) {
            log.debug("Помощь с ID {} найдена, но помечена как удаленная", id);
            throw new NotFoundException("Помощь не найдена с id: " + id);
        }

        return help;
    }

    /**
     * Получить граф помощи для пользователя.
     * Показывает только тех, кто пошёл ОТ пользователя (его помощь и её последствия)
     * Пользователь всегда в корне - это придает ему веса и значимости
     */
    public org.example.helptreeservice.dto.graph.HelpGraphDto getHelpGraph(Long userId) {
        return getHelpGraph(userId, null, null);
    }

    /**
     * Builds the confirmed-help graph.
     *
     * <p>Edges are directed helper -> receiver and their id is the help record
     * id. Only CONFIRMED, non-deleted helps are present, so the graph carries
     * exactly the same acts as ratings, achievements and statistics.</p>
     *
     * @param depth    optional maximum distance from {@code userId}; null means
     *                 the whole reachable component. Ignored for the global
     *                 graph.
     * @param maxEdges optional hard limit on returned edges. When it applies
     *                 the response is marked {@code truncated} and
     *                 {@code totalHelps} still reports the full subgraph size.
     */
    public org.example.helptreeservice.dto.graph.HelpGraphDto getHelpGraph(Long userId,
                                                                          Integer depth,
                                                                          Integer maxEdges) {
        long started = System.nanoTime();
        log.info("Построение графа помощи для userId={}, depth={}, maxEdges={}",
                userId, depth, maxEdges);

        List<ConfirmedHelpGraphProjection> confirmedHelps =
                helpRepository.findConfirmedHelpGraphProjection();

        Integer effectiveDepth = depth != null && depth >= 0 ? depth : null;
        int effectiveMaxEdges = maxEdges != null && maxEdges > 0 ? maxEdges : Integer.MAX_VALUE;

        Set<Long> selectedUserIds = selectGraphUsers(userId, effectiveDepth, confirmedHelps);

        Map<Long, User> usersMap = userRepository.findAllById(selectedUserIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Map<Long, org.example.helptreeservice.dto.graph.HelpGraphDto.Node> nodesMap =
                new LinkedHashMap<>();
        for (Long id : selectedUserIds) {
            User user = usersMap.get(id);
            if (user != null) {
                nodesMap.put(id, toGraphNode(user));
            }
        }

        List<org.example.helptreeservice.dto.graph.HelpGraphDto.Edge> matchingEdges =
                new ArrayList<>();
        for (ConfirmedHelpGraphProjection help : confirmedHelps) {
            if (selectedUserIds.contains(help.getHelperId())
                    && selectedUserIds.contains(help.getReceiverId())) {
                matchingEdges.add(org.example.helptreeservice.dto.graph.HelpGraphDto.Edge.builder()
                        // The edge identity is the help act, not the helper.
                        .id(help.getHelpId())
                        .fromUserId(help.getHelperId())
                        .fromUserName(help.getHelperName() != null ? help.getHelperName() : "")
                        .toUserId(help.getReceiverId())
                        .toUserName(help.getReceiverName() != null ? help.getReceiverName() : "")
                        .postId(help.getPostId())
                        .postTitle(help.getPostTitle() != null ? help.getPostTitle() : "")
                        .status(help.getStatus() != null ? help.getStatus().name() : "")
                        .confirmedAt(help.getConfirmedAt())
                        .build());
            }
        }

        boolean truncated = matchingEdges.size() > effectiveMaxEdges;
        List<org.example.helptreeservice.dto.graph.HelpGraphDto.Edge> returnedEdges =
                truncated
                        ? new ArrayList<>(matchingEdges.subList(0, effectiveMaxEdges))
                        : matchingEdges;

        long durationNanos = System.nanoTime() - started;
        helpGraphMetrics.record(userId == null, durationNanos, nodesMap.size(),
                matchingEdges.size(), returnedEdges.size(), truncated);

        log.info("Граф помощи построен: users={}, helps={}, returnedEdges={}, truncated={}, buildTimeMs={}",
                nodesMap.size(), matchingEdges.size(), returnedEdges.size(), truncated,
                Duration.ofNanos(durationNanos).toMillis());

        return org.example.helptreeservice.dto.graph.HelpGraphDto.builder()
                .nodes(new ArrayList<>(nodesMap.values()))
                .edges(returnedEdges)
                .totalHelps(matchingEdges.size())
                .returnedHelps(returnedEdges.size())
                .totalUsers(nodesMap.size())
                .truncated(truncated)
                .buildTimeMs(Duration.ofNanos(durationNanos).toMillis())
                .build();
    }

    /**
     * For a user graph: BFS over helper -> receiver edges up to {@code depth}.
     * For the global graph: every helper and receiver that appears in a
     * confirmed act.
     */
    private Set<Long> selectGraphUsers(Long userId,
                                       Integer depth,
                                       List<ConfirmedHelpGraphProjection> confirmedHelps) {
        Set<Long> selected = new HashSet<>();
        if (userId == null) {
            for (ConfirmedHelpGraphProjection help : confirmedHelps) {
                selected.add(help.getHelperId());
                selected.add(help.getReceiverId());
            }
            return selected;
        }

        Map<Long, List<ConfirmedHelpGraphProjection>> outgoing = new HashMap<>();
        for (ConfirmedHelpGraphProjection help : confirmedHelps) {
            outgoing.computeIfAbsent(help.getHelperId(), key -> new ArrayList<>()).add(help);
        }

        Map<Long, Integer> levelByUser = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        selected.add(userId);
        levelByUser.put(userId, 0);
        queue.add(userId);

        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            int currentLevel = levelByUser.get(current);
            if (depth != null && currentLevel >= depth) {
                continue;
            }
            for (ConfirmedHelpGraphProjection help : outgoing.getOrDefault(current, List.of())) {
                Long receiverId = help.getReceiverId();
                int receiverLevel = currentLevel + 1;
                Integer knownLevel = levelByUser.get(receiverId);
                if (knownLevel == null || receiverLevel < knownLevel) {
                    levelByUser.put(receiverId, receiverLevel);
                    selected.add(receiverId);
                    queue.addLast(receiverId);
                }
            }
        }
        return selected;
    }

    private org.example.helptreeservice.dto.graph.HelpGraphDto.Node toGraphNode(User user) {
        return org.example.helptreeservice.dto.graph.HelpGraphDto.Node.builder()
                .id(user.getId())
                .name(user.getName())
                .avatarUrl(imageService.refreshUrl(user.getAvatarUrl()))
                .helpedCount(user.getHelpedCount())
                .debtCount(user.getDebtCount())
                .rating(user.getRating())
                .build();
    }

    public org.example.helptreeservice.dto.graph.HelpStatsDto getHelpStats() {
        // SQL агрегация по месяцам
        Map<String, Long> byMonth = new LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -11);
        for (int i = 0; i < 12; i++) {
            String monthKey = String.format("%02d/%d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR));
            byMonth.put(monthKey, 0L);
            cal.add(Calendar.MONTH, 1);
        }
        
        List<Object[]> monthData = helpRepository.countConfirmedByMonth();
        for (Object[] row : monthData) {
            String monthKey = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            if (byMonth.containsKey(monthKey)) {
                byMonth.put(monthKey, count);
            }
        }
        
        // SQL агрегация по категориям
        Map<String, Long> byCategory = new HashMap<>();
        List<Object[]> categoryData = helpRepository.countConfirmedByCategory();
        for (Object[] row : categoryData) {
            String category = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            if (category != null) {
                byCategory.put(category, count);
            }
        }
        
        // SQL агрегация топ помогающих
        List<Object[]> helperData = helpRepository.countHelpsByHelper();
        List<Long> topHelperIds = new ArrayList<>();
        Map<Long, Long> helpCountByUser = new HashMap<>();
        for (Object[] row : helperData) {
            Long helperId = ((Number) row[0]).longValue();
            Long count = ((Number) row[1]).longValue();
            topHelperIds.add(helperId);
            helpCountByUser.put(helperId, count);
        }
        topHelperIds = topHelperIds.stream().limit(10).collect(Collectors.toList());
        
        Map<Long, String> userNames = new HashMap<>();
        if (!topHelperIds.isEmpty()) {
            List<User> topUsers = userRepository.findAllById(topHelperIds);
            for (User user : topUsers) {
                userNames.put(user.getId(), user.getName());
            }
        }
        
        List<org.example.helptreeservice.dto.graph.HelpStatsDto.TopHelper> topHelpersList = topHelperIds.stream()
            .map(id -> org.example.helptreeservice.dto.graph.HelpStatsDto.TopHelper.builder()
                .userId(id)
                .name(userNames.get(id))
                .helpCount(helpCountByUser.get(id))
                .build())
            .collect(Collectors.toList());
        
        long totalHelps = helpRepository.countByStatus(HelpStatus.CONFIRMED);
        long totalHelpRecords = helpRepository.countActive();
        long activeHelps = helpRepository.countByStatusIn(ACTIVE_HELP_STATUSES);
        long cancelledHelps = helpRepository.countByStatus(HelpStatus.CANCELLED);

        return org.example.helptreeservice.dto.graph.HelpStatsDto.builder()
                .totalHelps((int) totalHelps)
                .totalHelpRecords((int) totalHelpRecords)
                .activeHelps((int) activeHelps)
                .cancelledHelps((int) cancelledHelps)
                .byMonth(byMonth)
                .byCategory(byCategory)
                .topHelpers(topHelpersList)
                .build();
    }
}