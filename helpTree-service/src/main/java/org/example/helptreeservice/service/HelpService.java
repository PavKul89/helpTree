package org.example.helptreeservice.service;


import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.HelpMapper;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    /**
     * 1. Помощник откликается на пост
     */
    public HelpResponse acceptHelp(HelpRequest request) {
        log.info("Принятие помощи: helperId={} откликается на postId={}",
                request.getHelperId(), request.getPostId());

        try {
            Post post = postRepository.findById(request.getPostId())
                    .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + request.getPostId()));

            if (post.getDeleted() != null && post.getDeleted()) {
                log.warn("Попытка откликнуться на удаленный пост с ID: {}", request.getPostId());
                throw new NotFoundException("Пост не найден");
            }
            log.debug("Найден пост: title={}, автор={}, статус={}",
                    post.getTitle(), post.getUser().getEmail(), post.getStatus());

            User helper = userRepository.findById(request.getHelperId())
                    .orElseThrow(() -> new NotFoundException("Помощник не найден с id: " + request.getHelperId()));

            if (helper.getDeleted() != null && helper.getDeleted()) {
                log.warn("Попытка откликнуться от удаленного пользователя с ID: {}", request.getHelperId());
                throw new NotFoundException("Помощник не найден");
            }
            log.debug("Найден помощник: email={}, имя={}", helper.getEmail(), helper.getName());

            User receiver = post.getUser();
            log.debug("Автор поста (получатель): email={}, имя={}", receiver.getEmail(), receiver.getName());

            if (receiver.getBlockedAt() != null) {
                log.warn("Попытка откликнуться на пост заблокированного пользователя: receiverId={}", receiver.getId());
                throw new BadRequestException("Автор поста заблокирован за долг. Нельзя откликнуться на его пост.");
            }

            // Проверки
            if (helper.getId().equals(receiver.getId())) {
                log.warn("Попытка помочь самому себе: helperId={}, receiverId={}",
                        helper.getId(), receiver.getId());
                throw new BadRequestException("Нельзя помочь самому себе");
            }

            if (receiver.getDebtCount() > 5) {
                log.warn("Попытка откликнуться на пост пользователя с долгом: receiverId={}, debtCount={}",
                        receiver.getId(), receiver.getDebtCount());
                boolean isInGoodStanding = receiver.getHelpedCount() >= receiver.getDebtCount();
                if (!isInGoodStanding) {
                    throw new BadRequestException("Пользователь заблокирован за долг. Невозможно откликнуться на пост.");
                }
            }

            if (helpRepository.existsByPostAndHelper(post, helper)) {
                log.warn("Повторный отклик на пост: helperId={}, postId={}",
                        helper.getId(), post.getId());
                throw new ConflictException("Вы уже откликались на этот пост");
            }

            if (post.getHelper() != null) {
                log.warn("На пост уже есть помощник: postId={}, существующий helperId={}",
                        post.getId(), post.getHelper().getId());
                throw new ConflictException("На этот пост уже кто-то откликнулся");
            }

            // Создаем запись о помощи
            Help help = new Help();
            help.setPost(post);
            help.setHelper(helper);
            help.setReceiver(receiver);
            help.setStatus(HelpStatus.ACCEPTED);
            help.setAcceptedAt(LocalDateTime.now());
            help.setCreatedAt(LocalDateTime.now());
            help.setUpdatedAt(LocalDateTime.now());

            // Обновляем статус поста и помощника
            post.setStatus(PostStatus.IN_PROGRESS);
            post.setHelper(helper);
            postRepository.save(post);
            log.debug("Статус поста обновлен на IN_PROGRESS, назначен помощник ID: {}", helper.getId());

            Help savedHelp = helpRepository.save(help);

            log.info("Помощь успешно принята: helpId={}, helperId={}, receiverId={}, postId={}",
                    savedHelp.getId(), helper.getId(), receiver.getId(), post.getId());

            return helpMapper.toResponse(savedHelp);

        } catch (NotFoundException e) {
            log.warn("Не удалось принять помощь: {}", e.getMessage());
            throw e;
        } catch (BadRequestException | ConflictException e) {
            log.warn("Ошибка валидации при принятии помощи: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при принятии помощи: helperId={}, postId={}",
                    request.getHelperId(), request.getPostId(), e);
            throw e;
        }
    }

    /**
     * 2. Помощник отмечает, что помощь выполнена
     */
    public HelpResponse completeHelp(Long helpId) {
        log.info("Завершение помощи: helpId={}", helpId);

        try {
            Help help = getHelpById(helpId);
            log.debug("Текущее состояние помощи: status={}, helperId={}, receiverId={}",
                    help.getStatus(), help.getHelper().getId(), help.getReceiver().getId());

            // Сохраняем время принятия для расчета длительности
            LocalDateTime acceptedAt = help.getAcceptedAt();

            // Проверки
            if (help.getStatus() == HelpStatus.COMPLETED) {
                log.warn("Попытка завершить уже выполненную помощь: helpId={}", helpId);
                throw new ConflictException("Помощь уже отмечена как выполненная");
            }

            if (help.getStatus() == HelpStatus.CANCELLED) {
                log.warn("Попытка завершить отмененную помощь: helpId={}", helpId);
                throw new ConflictException("Нельзя завершить отмененную помощь");
            }

            if (help.getStatus() == HelpStatus.CONFIRMED) {
                log.warn("Попытка завершить подтвержденную помощь: helpId={}", helpId);
                throw new ConflictException("Помощь уже подтверждена");
            }

            if (help.getStatus() != HelpStatus.ACCEPTED) {
                log.warn("Попытка завершить помощь в неверном статусе: helpId={}, currentStatus={}",
                        helpId, help.getStatus());
                throw new BadRequestException("Помощь не в статусе ACCEPTED");
            }

            help.setStatus(HelpStatus.COMPLETED);
            help.setCompletedAt(LocalDateTime.now());
            help.setUpdatedAt(LocalDateTime.now());

            Help updatedHelp = helpRepository.save(help);

            // Рассчитываем длительность (в минутах)
            long duration = 0;
            if (acceptedAt != null) {
                duration = ChronoUnit.MINUTES.between(acceptedAt, LocalDateTime.now());
            }

            ratingService.updateStatsAfterHelp(help.getHelper().getId(), help.getReceiver().getId(), true);

            log.info("Помощь успешно отмечена как выполненная: helpId={}", helpId);
            log.debug("Обновленный статус помощи: {}", updatedHelp.getStatus());

            return helpMapper.toResponse(updatedHelp);

        } catch (NotFoundException e) {
            log.warn("Не удалось завершить помощь: helpId={} не найден", helpId);
            throw e;
        } catch (BadRequestException | ConflictException e) {
            log.warn("Ошибка валидации при завершении помощи: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при завершении помощи: helpId={}", helpId, e);
            throw e;
        }
    }

    /**
     * 3. Автор подтверждает, что помощь получена
     */
    public HelpResponse confirmHelp(Long helpId) {
        log.info("Подтверждение получения помощи: helpId={}", helpId);

        try {
            Help help = getHelpById(helpId);
            log.debug("Текущее состояние помощи: status={}, helperId={}, receiverId={}",
                    help.getStatus(), help.getHelper().getId(), help.getReceiver().getId());

            // Проверки
            if (help.getStatus() == HelpStatus.CONFIRMED) {
                log.warn("Попытка подтвердить уже подтвержденную помощь: helpId={}", helpId);
                throw new ConflictException("Помощь уже подтверждена");
            }

            if (help.getStatus() == HelpStatus.CANCELLED) {
                log.warn("Попытка подтвердить отмененную помощь: helpId={}", helpId);
                throw new ConflictException("Нельзя подтвердить отмененную помощь");
            }

            if (help.getStatus() != HelpStatus.COMPLETED) {
                log.warn("Попытка подтвердить помощь в неверном статусе: helpId={}, currentStatus={}",
                        helpId, help.getStatus());
                throw new BadRequestException("Помощь не в статусе COMPLETED");
            }

            // Проверяем, не заблокирован ли получатель помощи
            if (userService.isUserBlocked(help.getReceiver().getId())) {
                log.warn("Попытка подтвердить помощь заблокированному пользователю: receiverId={}", 
                        help.getReceiver().getId());
                throw new BadRequestException("Нельзя подтвердить помощь заблокированному пользователю");
            }

            help.setStatus(HelpStatus.CONFIRMED);
            help.setConfirmedAt(LocalDateTime.now());
            help.setUpdatedAt(LocalDateTime.now());

            // Обновляем статус поста
            Post post = help.getPost();
            post.setStatus(PostStatus.COMPLETED);
            postRepository.save(post);
            log.debug("Статус поста ID={} обновлен на COMPLETED", post.getId());

            // Правило пирамиды
            log.info("Применение правила пирамиды: helperId={}, receiverId={}",
                    help.getHelper().getId(), help.getReceiver().getId());

            userService.incrementHelpedCount(help.getReceiver().getId());
            userService.userHelpedSomeone(help.getHelper().getId());

            // Начисляем HelpCoins за помощь
            walletService.addCoinsForHelp(help.getHelper().getId(), help.getReceiver().getId(), post.getId());
            walletService.addCoinsForReceivedHelp(help.getReceiver().getId(), help.getHelper().getId(), post.getId());

            achievementService.checkAndAwardAchievements(help.getHelper(), help);

            Help updatedHelp = helpRepository.save(help);

            ratingService.updateStatsAfterHelp(help.getHelper().getId(), help.getReceiver().getId(), true);

            log.info("Помощь успешно подтверждена: helpId={}", helpId);
            log.debug("Итоговое состояние помощи: {}", updatedHelp.getStatus());

            return helpMapper.toResponse(updatedHelp);

        } catch (NotFoundException e) {
            log.warn("Не удалось подтвердить помощь: helpId={} не найден", helpId);
            throw e;
        } catch (BadRequestException | ConflictException e) {
            log.warn("Ошибка валидации при подтверждении помощи: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при подтверждении помощи: helpId={}", helpId, e);
            throw e;
        }
    }

    /**
     * 4. Отмена помощи
     */
    public HelpResponse cancelHelp(Long helpId) {
        log.info("Отмена помощи: helpId={}", helpId);

        try {
            Help help = getHelpById(helpId);
            log.debug("Текущее состояние помощи: status={}, helperId={}, receiverId={}",
                    help.getStatus(), help.getHelper().getId(), help.getReceiver().getId());

            if (help.getStatus() == HelpStatus.CANCELLED) {
                log.warn("Попытка отменить уже отмененную помощь: helpId={}", helpId);
                throw new ConflictException("Помощь уже отменена");
            }

            if (help.getStatus() == HelpStatus.CONFIRMED) {
                log.warn("Попытка отменить подтвержденную помощь: helpId={}", helpId);
                throw new ConflictException("Нельзя отменить подтвержденную помощь");
            }

            HelpStatus oldStatus = help.getStatus();
            help.setStatus(HelpStatus.CANCELLED);
            help.setUpdatedAt(LocalDateTime.now());
            log.debug("Статус помощи изменен с {} на CANCELLED", oldStatus);

            Post post = help.getPost();
            post.setStatus(PostStatus.OPEN);
            post.setHelper(null);
            postRepository.save(post);
            log.debug("Статус поста ID={} возвращен на OPEN, помощник удален", post.getId());

            Help updatedHelp = helpRepository.save(help);

            ratingService.updateStatsAfterHelp(help.getHelper().getId(), help.getReceiver().getId(), false);

            log.info("Помощь успешно отменена: helpId={}", helpId);

            return helpMapper.toResponse(updatedHelp);

        } catch (NotFoundException e) {
            log.warn("Не удалось отменить помощь: helpId={} не найден", helpId);
            throw e;
        } catch (BadRequestException | ConflictException e) {
            log.warn("Ошибка валидации при отмене помощи: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при отмене помощи: helpId={}", helpId, e);
            throw e;
        }
    }

    /**
     * Получить все помощи пользователя (где он помогал)
     */
    @Transactional(readOnly = true)
    public List<HelpResponse> getHelpsByPost(Long postId) {
        return helpRepository.findByPostId(postId).stream()
                .map(helpMapper::toResponse)
                .collect(Collectors.toList());
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

    /**
     * Получить граф помощи для пользователя.
     * Показывает только тех, кто пошёл ОТ пользователя (его помощь и её последствия)
     * Пользователь всегда в корне - это придает ему веса и значимости
     */
    public org.example.helptreeservice.dto.graph.HelpGraphDto getHelpGraph(Long userId) {
        log.info("Построение графа помощи для userId: {}", userId);
        
        List<Object[]> graphData = helpRepository.findConfirmedHelpGraphData();
        
        if (userId == null) {
            return buildFullGraphFromSql(graphData);
        }
        
        // Строим карту: кто помог → список кому помогли
        Map<Long, List<Long>> helperToReceivers = new HashMap<>();
        
        for (Object[] row : graphData) {
            Long helperId = ((Number) row[0]).longValue();
            Long receiverId = ((Number) row[2]).longValue();
            helperToReceivers.computeIfAbsent(helperId, k -> new ArrayList<>()).add(receiverId);
        }
        
        // Собираем только тех, кто идёт ОТ userId (его дети и их дети)
        Set<Long> visitedUserIds = new HashSet<>();
        visitedUserIds.add(userId);
        
        List<Long> toProcess = new ArrayList<>();
        toProcess.add(userId);
        
        while (!toProcess.isEmpty()) {
            Long currentId = toProcess.remove(0);
            
            List<Long> iHelped = helperToReceivers.get(currentId);
            if (iHelped != null) {
                for (Long helpedId : iHelped) {
                    if (!visitedUserIds.contains(helpedId)) {
                        visitedUserIds.add(helpedId);
                        toProcess.add(helpedId);
                    }
                }
            }
        }
        
        // Загружаем всех пользователей одним запросом
        List<User> allUsers = userRepository.findAllById(visitedUserIds);
        Map<Long, User> usersMap = new HashMap<>();
        for (User user : allUsers) {
            usersMap.put(user.getId(), user);
        }
        
        // Создаем узлы
        Map<Long, org.example.helptreeservice.dto.graph.HelpGraphDto.Node> nodesMap = new HashMap<>();
        
        User currentUser = usersMap.get(userId);
        if (currentUser != null) {
            nodesMap.put(userId, org.example.helptreeservice.dto.graph.HelpGraphDto.Node.builder()
                    .id(userId)
                    .name(currentUser.getName())
                    .avatarUrl(imageService.refreshUrl(currentUser.getAvatarUrl()))
                    .helpedCount(currentUser.getHelpedCount())
                    .debtCount(currentUser.getDebtCount())
                    .rating(currentUser.getRating())
                    .build());
        }
        
        for (Long id : visitedUserIds) {
            if (id.equals(userId)) continue;
            User user = usersMap.get(id);
            if (user != null) {
                nodesMap.put(id, org.example.helptreeservice.dto.graph.HelpGraphDto.Node.builder()
                        .id(id)
                        .name(user.getName())
                        .avatarUrl(imageService.refreshUrl(user.getAvatarUrl()))
                        .helpedCount(user.getHelpedCount())
                        .debtCount(user.getDebtCount())
                        .rating(user.getRating())
                        .build());
            }
        }
        
        // Создаем рёбра
        List<org.example.helptreeservice.dto.graph.HelpGraphDto.Edge> edges = new ArrayList<>();
        for (Object[] row : graphData) {
            Long fromId = ((Number) row[0]).longValue();
            String fromName = row[1] != null ? row[1].toString() : "";
            Long toId = ((Number) row[2]).longValue();
            String toName = row[3] != null ? row[3].toString() : "";
            Long postId = ((Number) row[4]).longValue();
            String postTitle = row[5] != null ? row[5].toString() : "";
            String status = row[6] != null ? row[6].toString() : "";
            LocalDateTime confirmedAt = row[7] instanceof LocalDateTime ? (LocalDateTime) row[7] : null;
            
            if (visitedUserIds.contains(fromId) && visitedUserIds.contains(toId)) {
                edges.add(org.example.helptreeservice.dto.graph.HelpGraphDto.Edge.builder()
                        .id(((Number) row[0]).longValue())
                        .fromUserId(fromId)
                        .fromUserName(fromName)
                        .toUserId(toId)
                        .toUserName(toName)
                        .postTitle(postTitle)
                        .status(status)
                        .confirmedAt(confirmedAt)
                        .build());
            }
        }
        
        return org.example.helptreeservice.dto.graph.HelpGraphDto.builder()
                .nodes(new ArrayList<>(nodesMap.values()))
                .edges(edges)
                .totalHelps(edges.size())
                .totalUsers(nodesMap.size())
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
        
        long totalHelps = helpRepository.countActive();
        
        return org.example.helptreeservice.dto.graph.HelpStatsDto.builder()
                .totalHelps((int) totalHelps)
                .byMonth(byMonth)
                .byCategory(byCategory)
                .topHelpers(topHelpersList)
                .build();
    }
    
    /**
     * Построить полный граф всех пользователей (без фильтрации) из SQL данных
     */
    private org.example.helptreeservice.dto.graph.HelpGraphDto buildFullGraphFromSql(List<Object[]> graphData) {
        Map<Long, org.example.helptreeservice.dto.graph.HelpGraphDto.Node> nodesMap = new HashMap<>();
        
        for (Object[] row : graphData) {
            Long helperId = ((Number) row[0]).longValue();
            String helperName = row[1] != null ? row[1].toString() : "";
            Long receiverId = ((Number) row[2]).longValue();
            String receiverName = row[3] != null ? row[3].toString() : "";
            
            if (!nodesMap.containsKey(helperId)) {
                User helper = userRepository.findById(helperId).orElse(null);
                if (helper != null) {
                    nodesMap.put(helperId, org.example.helptreeservice.dto.graph.HelpGraphDto.Node.builder()
                            .id(helperId)
                            .name(helper.getName())
                            .avatarUrl(imageService.refreshUrl(helper.getAvatarUrl()))
                            .helpedCount(helper.getHelpedCount())
                            .debtCount(helper.getDebtCount())
                            .rating(helper.getRating())
                            .build());
                }
            }
            
            if (!nodesMap.containsKey(receiverId)) {
                User receiver = userRepository.findById(receiverId).orElse(null);
                if (receiver != null) {
                    nodesMap.put(receiverId, org.example.helptreeservice.dto.graph.HelpGraphDto.Node.builder()
                            .id(receiverId)
                            .name(receiver.getName())
                            .avatarUrl(imageService.refreshUrl(receiver.getAvatarUrl()))
                            .helpedCount(receiver.getHelpedCount())
                            .debtCount(receiver.getDebtCount())
                            .rating(receiver.getRating())
                            .build());
                }
            }
        }
        
        List<org.example.helptreeservice.dto.graph.HelpGraphDto.Edge> edges = new ArrayList<>();
        for (Object[] row : graphData) {
            Long fromId = ((Number) row[0]).longValue();
            String fromName = row[1] != null ? row[1].toString() : "";
            Long toId = ((Number) row[2]).longValue();
            String toName = row[3] != null ? row[3].toString() : "";
            String postTitle = row[5] != null ? row[5].toString() : "";
            String status = row[6] != null ? row[6].toString() : "";
            LocalDateTime confirmedAt = row[7] instanceof LocalDateTime ? (LocalDateTime) row[7] : null;
            
            edges.add(org.example.helptreeservice.dto.graph.HelpGraphDto.Edge.builder()
                    .id(fromId)
                    .fromUserId(fromId)
                    .fromUserName(fromName)
                    .toUserId(toId)
                    .toUserName(toName)
                    .postTitle(postTitle)
                    .status(status)
                    .confirmedAt(confirmedAt)
                    .build());
        }
        
        return org.example.helptreeservice.dto.graph.HelpGraphDto.builder()
                .nodes(new ArrayList<>(nodesMap.values()))
                .edges(edges)
                .totalHelps(graphData.size())
                .totalUsers(nodesMap.size())
                .build();
    }
}