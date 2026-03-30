package org.example.helptreeservice.service;


import org.example.helptreeservice.dto.HelpEvent;
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
    private final KafkaProducerService kafkaProducerService;
    private final AchievementService achievementService;

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

            if (receiver.getBlockedAt() != null) {
                log.warn("Попытка откликнуться на пост заблокированного пользователя: receiverId={}", receiver.getId());
                throw new BadRequestException("Пользователь заблокирован за долг. Невозможно откликнуться на пост.");
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

            // Отправляем событие в Kafka
            HelpEvent event = HelpEvent.builder()
                    .helpId(savedHelp.getId())
                    .postId(post.getId())
                    .postTitle(post.getTitle())
                    .authorId(receiver.getId())
                    .authorEmail(receiver.getEmail())
                    .authorName(receiver.getName())
                    .helperId(helper.getId())
                    .helperEmail(helper.getEmail())
                    .helperName(helper.getName())
                    .receiverId(receiver.getId())
                    .eventType("HELP_ACCEPTED")
                    .timestamp(LocalDateTime.now())
                    .build();
            kafkaProducerService.sendHelpEvent(event);

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

            // Отправляем событие в Kafka
            HelpEvent event = HelpEvent.builder()
                    .postId(help.getPost().getId())
                    .postTitle(help.getPost().getTitle())
                    .authorId(help.getReceiver().getId())
                    .authorEmail(help.getReceiver().getEmail())
                    .authorName(help.getReceiver().getName())
                    .helperId(help.getHelper().getId())
                    .helperEmail(help.getHelper().getEmail())
                    .helperName(help.getHelper().getName())
                    .receiverId(help.getReceiver().getId())
                    .eventType("HELP_COMPLETED")
                    .timestamp(LocalDateTime.now())
                    .duration(duration)
                    .build();
            kafkaProducerService.sendHelpEvent(event);

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

            achievementService.checkAndAwardAchievements(help.getHelper(), help);

            Help updatedHelp = helpRepository.save(help);

            // Отправляем событие в Kafka
            HelpEvent event = HelpEvent.builder()
                    .helpId(updatedHelp.getId())
                    .postId(post.getId())
                    .postTitle(post.getTitle())
                    .authorId(help.getReceiver().getId())
                    .authorEmail(help.getReceiver().getEmail())
                    .authorName(help.getReceiver().getName())
                    .helperId(help.getHelper().getId())
                    .helperEmail(help.getHelper().getEmail())
                    .helperName(help.getHelper().getName())
                    .receiverId(help.getReceiver().getId())
                    .eventType("HELP_CONFIRMED")
                    .timestamp(LocalDateTime.now())
                    .build();
            kafkaProducerService.sendHelpEvent(event);

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

            // Отправляем событие в Kafka
            HelpEvent event = HelpEvent.builder()
                    .helpId(updatedHelp.getId())
                    .postId(post.getId())
                    .postTitle(post.getTitle())
                    .authorId(help.getReceiver().getId())
                    .authorEmail(help.getReceiver().getEmail())
                    .authorName(help.getReceiver().getName())
                    .helperId(help.getHelper().getId())
                    .helperEmail(help.getHelper().getEmail())
                    .helperName(help.getHelper().getName())
                    .receiverId(help.getReceiver().getId())
                    .eventType("HELP_CANCELLED")
                    .timestamp(LocalDateTime.now())
                    .build();
            kafkaProducerService.sendHelpEvent(event);

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
    public List<HelpResponse> getHelpsByHelper(Long helperId) {
        log.info("Запрос всех откликов помощника с ID: {}", helperId);

        try {
            User helper = userRepository.findById(helperId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + helperId));

            if (helper.getDeleted() != null && helper.getDeleted()) {
                log.warn("Попытка получить помощи удаленного пользователя: helperId={}", helperId);
                throw new NotFoundException("Пользователь не найден");
            }

            List<HelpResponse> helps = helpRepository.findByHelperWithDetails(helper).stream()
                    .map(helpMapper::toResponse)
                    .collect(Collectors.toList());

            log.info("Найдено {} откликов для помощника с ID: {}", helps.size(), helperId);
            log.debug("ID откликов помощника {}: {}", helperId,
                    helps.stream().map(HelpResponse::getId).collect(Collectors.toList()));

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
    public List<HelpResponse> getHelpsByReceiver(Long receiverId) {
        log.info("Запрос всех полученных помощью пользователя с ID: {}", receiverId);

        try {
            User receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + receiverId));

            if (receiver.getDeleted() != null && receiver.getDeleted()) {
                log.warn("Попытка получить помощи удаленного пользователя: receiverId={}", receiverId);
                throw new NotFoundException("Пользователь не найден");
            }

            List<HelpResponse> helps = helpRepository.findByReceiverWithDetails(receiver).stream()
                    .map(helpMapper::toResponse)
                    .collect(Collectors.toList());

            log.info("Найдено {} полученных помощью для пользователя с ID: {}", helps.size(), receiverId);
            log.debug("ID полученных помощью пользователя {}: {}", receiverId,
                    helps.stream().map(HelpResponse::getId).collect(Collectors.toList()));

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

        LocalDateTime lastLogin;
        if (sinceParam != null && !sinceParam.isEmpty()) {
            lastLogin = LocalDateTime.parse(sinceParam);
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + userId));
            lastLogin = user.getLastLogin();
            if (lastLogin == null) {
                lastLogin = LocalDateTime.of(1970, 1, 1, 0, 0);
            }
        }

        List<Post> userPosts = postRepository.findByUserId(userId);

        long count = 0;
        for (Post post : userPosts) {
            List<Help> helps = helpRepository.findByPostWithHelper(post);
            for (Help help : helps) {
                if (help.getCreatedAt() != null && help.getCreatedAt().isAfter(lastLogin)) {
                    count++;
                }
            }
        }

        log.info("Найдено {} новых ответов для userId: {}", count, userId);
        return count;
    }

    @Transactional(readOnly = true)
    public List<org.example.helptreeservice.dto.helps.NewResponseDto> getNewResponses(Long userId, String sinceParam) {
        log.info("Получение ответов для userId: {}", userId);

        List<Post> userPosts = postRepository.findByUserId(userId);
        List<org.example.helptreeservice.dto.helps.NewResponseDto> responses = new ArrayList<>();

        for (Post post : userPosts) {
            List<Help> helps = helpRepository.findByPostWithHelper(post);
            for (Help help : helps) {
                org.example.helptreeservice.dto.helps.NewResponseDto dto = new org.example.helptreeservice.dto.helps.NewResponseDto();
                dto.setHelpId(help.getId());
                dto.setPostId(post.getId());
                dto.setPostTitle(post.getTitle());
                String helperName = "Неизвестно";
                if (help.getHelper() != null) {
                    helperName = help.getHelper().getName();
                }
                dto.setHelperName(helperName);
                if (help.getCreatedAt() != null) {
                    dto.setCreatedAt(help.getCreatedAt().toString());
                }
                responses.add(dto);
            }
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
     * Получить граф помощи для пользователя
     * Показывает только тех, кто пошёл ОТ пользователя (его помощь и её последствия)
     * Пользователь всегда в корне - это придает ему веса и значимости
     */
    public org.example.helptreeservice.dto.graph.HelpGraphDto getHelpGraph(Long userId) {
        log.info("Построение графа помощи для userId: {}", userId);
        
        List<Help> confirmedHelps = helpRepository.findByStatusAndDeletedFalse(org.example.helptreeservice.enums.HelpStatus.CONFIRMED);
        
        if (userId == null) {
            return buildFullGraph(confirmedHelps);
        }
        
        // Строим карту: кто помог → список кому помогли
        Map<Long, List<Long>> helperToReceivers = new HashMap<>();
        
        for (Help help : confirmedHelps) {
            Long helperId = help.getHelper().getId();
            Long receiverId = help.getReceiver().getId();
            helperToReceivers.computeIfAbsent(helperId, k -> new ArrayList<>()).add(receiverId);
        }
        
        // Собираем только тех, кто идёт ОТ userId (его дети и их дети)
        Set<Long> visitedUserIds = new HashSet<>();
        visitedUserIds.add(userId);
        
        List<Long> toProcess = new ArrayList<>();
        toProcess.add(userId);
        
        while (!toProcess.isEmpty()) {
            Long currentId = toProcess.remove(0);
            
            // Кому currentId помог (он был helper) - это его дети в цепочке
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
        
        // Создаем узлы
        Map<Long, org.example.helptreeservice.dto.graph.HelpGraphDto.Node> nodesMap = new HashMap<>();
        
        // Добавляем текущего пользователя первым (корень)
        User currentUser = userRepository.findById(userId).orElse(null);
        if (currentUser != null) {
            nodesMap.put(userId, org.example.helptreeservice.dto.graph.HelpGraphDto.Node.builder()
                    .id(userId)
                    .name(currentUser.getName())
                    .avatarUrl(currentUser.getAvatarUrl())
                    .helpedCount(currentUser.getHelpedCount())
                    .debtCount(currentUser.getDebtCount())
                    .rating(currentUser.getRating())
                    .build());
        }
        
        // Добавляем остальных
        for (Long id : visitedUserIds) {
            if (id.equals(userId)) continue;
            User user = userRepository.findById(id).orElse(null);
            if (user != null) {
                nodesMap.put(id, org.example.helptreeservice.dto.graph.HelpGraphDto.Node.builder()
                        .id(id)
                        .name(user.getName())
                        .avatarUrl(user.getAvatarUrl())
                        .helpedCount(user.getHelpedCount())
                        .debtCount(user.getDebtCount())
                        .rating(user.getRating())
                        .build());
            }
        }
        
        // Создаем рёбра (только от userId и далее по цепочке)
        List<org.example.helptreeservice.dto.graph.HelpGraphDto.Edge> edges = new ArrayList<>();
        for (Help help : confirmedHelps) {
            Long fromId = help.getHelper().getId();
            Long toId = help.getReceiver().getId();
            
            if (visitedUserIds.contains(fromId) && visitedUserIds.contains(toId)) {
                edges.add(org.example.helptreeservice.dto.graph.HelpGraphDto.Edge.builder()
                        .id(help.getId())
                        .fromUserId(fromId)
                        .fromUserName(help.getHelper().getName())
                        .toUserId(toId)
                        .toUserName(help.getReceiver().getName())
                        .postTitle(help.getPost().getTitle())
                        .status(help.getStatus().name())
                        .confirmedAt(help.getConfirmedAt())
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
        List<Help> allHelps = helpRepository.findAllWithDetails();
        
        // По месяцам
        Map<String, Long> byMonth = new LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -11);
        for (int i = 0; i < 12; i++) {
            String monthKey = String.format("%02d/%d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR));
            byMonth.put(monthKey, 0L);
            cal.add(Calendar.MONTH, 1);
        }
        
        for (Help help : allHelps) {
            if (help.getConfirmedAt() != null) {
                String monthKey = String.format("%02d/%d", 
                    help.getConfirmedAt().getMonthValue(), 
                    help.getConfirmedAt().getYear());
                if (byMonth.containsKey(monthKey)) {
                    byMonth.put(monthKey, byMonth.get(monthKey) + 1);
                }
            }
        }
        
        // По категориям
        Map<String, Long> byCategory = new HashMap<>();
        for (Help help : allHelps) {
            if (help.getPost() != null && help.getPost().getCategory() != null) {
                String category = help.getPost().getCategory();
                byCategory.put(category, byCategory.getOrDefault(category, 0L) + 1);
            }
        }
        
        // Топ помогающих
        Map<Long, Long> helpCountByUser = new HashMap<>();
        for (Help help : allHelps) {
            Long helperId = help.getHelper().getId();
            helpCountByUser.put(helperId, helpCountByUser.getOrDefault(helperId, 0L) + 1);
        }
        
        List<Long> topHelpers = helpCountByUser.entrySet().stream()
            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        Map<Long, String> userNames = new HashMap<>();
        for (Long userId : topHelpers) {
            userRepository.findById(userId).ifPresent(u -> userNames.put(userId, u.getName()));
        }
        
        final Map<Long, Long> finalHelpCountByUser = helpCountByUser;
        List<org.example.helptreeservice.dto.graph.HelpStatsDto.TopHelper> topHelpersList = topHelpers.stream()
            .map(id -> org.example.helptreeservice.dto.graph.HelpStatsDto.TopHelper.builder()
                .userId(id)
                .name(userNames.get(id))
                .helpCount(finalHelpCountByUser.get(id))
                .build())
            .collect(Collectors.toList());
        
        return org.example.helptreeservice.dto.graph.HelpStatsDto.builder()
                .totalHelps(allHelps.size())
                .byMonth(byMonth)
                .byCategory(byCategory)
                .topHelpers(topHelpersList)
                .build();
    }
    
    /**
     * Построить полный граф всех пользователей (без фильтрации)
     */
    private org.example.helptreeservice.dto.graph.HelpGraphDto buildFullGraph(List<Help> confirmedHelps) {
        Map<Long, org.example.helptreeservice.dto.graph.HelpGraphDto.Node> nodesMap = new HashMap<>();
        
        for (Help help : confirmedHelps) {
            Long helperId = help.getHelper().getId();
            if (!nodesMap.containsKey(helperId)) {
                nodesMap.put(helperId, org.example.helptreeservice.dto.graph.HelpGraphDto.Node.builder()
                        .id(helperId)
                        .name(help.getHelper().getName())
                        .avatarUrl(help.getHelper().getAvatarUrl())
                        .helpedCount(help.getHelper().getHelpedCount())
                        .debtCount(help.getHelper().getDebtCount())
                        .rating(help.getHelper().getRating())
                        .build());
            }
            
            Long receiverId = help.getReceiver().getId();
            if (!nodesMap.containsKey(receiverId)) {
                nodesMap.put(receiverId, org.example.helptreeservice.dto.graph.HelpGraphDto.Node.builder()
                        .id(receiverId)
                        .name(help.getReceiver().getName())
                        .avatarUrl(help.getReceiver().getAvatarUrl())
                        .helpedCount(help.getReceiver().getHelpedCount())
                        .debtCount(help.getReceiver().getDebtCount())
                        .rating(help.getReceiver().getRating())
                        .build());
            }
        }
        
        List<org.example.helptreeservice.dto.graph.HelpGraphDto.Edge> edges = new ArrayList<>();
        for (Help help : confirmedHelps) {
            edges.add(org.example.helptreeservice.dto.graph.HelpGraphDto.Edge.builder()
                    .id(help.getId())
                    .fromUserId(help.getHelper().getId())
                    .fromUserName(help.getHelper().getName())
                    .toUserId(help.getReceiver().getId())
                    .toUserName(help.getReceiver().getName())
                    .postTitle(help.getPost().getTitle())
                    .status(help.getStatus().name())
                    .confirmedAt(help.getConfirmedAt())
                    .build());
        }
        
        return org.example.helptreeservice.dto.graph.HelpGraphDto.builder()
                .nodes(new ArrayList<>(nodesMap.values()))
                .edges(edges)
                .totalHelps(confirmedHelps.size())
                .totalUsers(nodesMap.size())
                .build();
    }
}