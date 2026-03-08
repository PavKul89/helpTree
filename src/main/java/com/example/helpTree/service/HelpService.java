package com.example.helpTree.service;

import com.example.helpTree.dto.helps.HelpRequest;
import com.example.helpTree.dto.helps.HelpResponse;
import com.example.helpTree.entity.Help;
import com.example.helpTree.entity.Post;
import com.example.helpTree.entity.User;
import com.example.helpTree.enums.HelpStatus;
import com.example.helpTree.enums.PostStatus;
import com.example.helpTree.exception.BadRequestException;
import com.example.helpTree.exception.ConflictException;
import com.example.helpTree.exception.NotFoundException;
import com.example.helpTree.mapper.HelpMapper;
import com.example.helpTree.repository.HelpRepository;
import com.example.helpTree.repository.PostRepository;
import com.example.helpTree.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
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

            User receiver = post.getUser(); // Автор поста
            log.debug("Автор поста (получатель): email={}, имя={}", receiver.getEmail(), receiver.getName());

            // Проверки
            if (helper.getId().equals(receiver.getId())) {
                log.warn("Попытка помочь самому себе: helperId={}, receiverId={}",
                        helper.getId(), receiver.getId());
                throw new BadRequestException("Нельзя помочь самому себе");
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

            // 👇 ВАЖНО: Обновляем статус поста и помощника
            post.setStatus(PostStatus.IN_PROGRESS);  // Меняем статус поста
            post.setHelper(helper);                   // Указываем, кто помогает
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

            // 👇 ПРОВЕРКИ
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
     * ЗДЕСЬ СРАБАТЫВАЕТ ПРАВИЛО ПИРАМИДЫ!
     */
    public HelpResponse confirmHelp(Long helpId) {
        log.info("Подтверждение получения помощи: helpId={}", helpId);

        try {
            Help help = getHelpById(helpId);
            log.debug("Текущее состояние помощи: status={}, helperId={}, receiverId={}",
                    help.getStatus(), help.getHelper().getId(), help.getReceiver().getId());

            // 👇 ПРОВЕРКИ
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

            Help updatedHelp = helpRepository.save(help);
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

            validateHelpNotInStatus(help, HelpStatus.CANCELLED, "Помощь уже отменена");
            validateHelpNotInStatus(help, HelpStatus.CONFIRMED, "Нельзя отменить подтвержденную помощь");

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
    public List<HelpResponse> getHelpsByHelper(Long helperId) {
        log.info("Запрос всех откликов помощника с ID: {}", helperId);

        try {
            User helper = userRepository.findById(helperId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + helperId));

            if (helper.getDeleted() != null && helper.getDeleted()) {
                log.warn("Попытка получить помощи удаленного пользователя: helperId={}", helperId);
                throw new NotFoundException("Пользователь не найден");
            }

            List<HelpResponse> helps = helpRepository.findByHelper(helper).stream()
                    .filter(h -> h.getDeleted() == null || !h.getDeleted())
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

            List<HelpResponse> helps = helpRepository.findByReceiver(receiver).stream()
                    .filter(h -> h.getDeleted() == null || !h.getDeleted())
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

    private Help getHelpById(Long id) {
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

    private void validateHelpStatus(Help help, HelpStatus expectedStatus, String errorMessage) {
        if (help.getStatus() != expectedStatus) {
            log.debug("Валидация статуса не пройдена: ожидался {}, текущий {}",
                    expectedStatus, help.getStatus());
            throw new BadRequestException(errorMessage);
        }
        log.debug("Валидация статуса пройдена: текущий статус {}", help.getStatus());
    }

    private void validateHelpNotInStatus(Help help, HelpStatus forbiddenStatus, String errorMessage) {
        if (help.getStatus() == forbiddenStatus) {
            log.debug("Валидация статуса не пройдена: статус {} запрещен", forbiddenStatus);
            throw new ConflictException(errorMessage);
        }
        log.debug("Валидация статуса пройдена: статус {} разрешен", help.getStatus());
    }
}