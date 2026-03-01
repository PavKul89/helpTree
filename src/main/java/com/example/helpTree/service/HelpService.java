package com.example.helpTree.service;

import com.example.helpTree.dto.helps.HelpRequest;
import com.example.helpTree.dto.helps.HelpResponse;
import com.example.helpTree.entity.Help;
import com.example.helpTree.entity.Post;
import com.example.helpTree.entity.User;
import com.example.helpTree.enums.HelpStatus;
import com.example.helpTree.enums.PostStatus;
import com.example.helpTree.mapper.HelpMapper;
import com.example.helpTree.repository.HelpRepository;
import com.example.helpTree.repository.PostRepository;
import com.example.helpTree.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
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
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new RuntimeException("Пост не найден"));

        User helper = userRepository.findById(request.getHelperId())
                .orElseThrow(() -> new RuntimeException("Помощник не найден"));

        User receiver = post.getUser(); // Автор поста

        // Проверки
        if (helper.getId().equals(receiver.getId())) {
            throw new RuntimeException("Нельзя помочь самому себе");
        }

        if (helpRepository.existsByPostAndHelper(post, helper)) {
            throw new RuntimeException("Вы уже откликались на этот пост");
        }

        if (post.getHelper() != null) {
            throw new RuntimeException("На этот пост уже кто-то откликнулся");
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

        Help savedHelp = helpRepository.save(help);
        return helpMapper.toResponse(savedHelp);
    }

    /**
     * 2. Помощник отмечает, что помощь выполнена
     */
    public HelpResponse completeHelp(Long helpId) {
        Help help = getHelpById(helpId);

        // 👇 ПРОВЕРКИ
        if (help.getStatus() == HelpStatus.COMPLETED) {
            throw new RuntimeException("Помощь уже отмечена как выполненная");
        }

        if (help.getStatus() == HelpStatus.CANCELLED) {
            throw new RuntimeException("Нельзя завершить отмененную помощь");
        }

        if (help.getStatus() == HelpStatus.CONFIRMED) {
            throw new RuntimeException("Помощь уже подтверждена");
        }

        if (help.getStatus() != HelpStatus.ACCEPTED) {
            throw new RuntimeException("Помощь не в статусе ACCEPTED");
        }

        help.setStatus(HelpStatus.COMPLETED);
        help.setCompletedAt(LocalDateTime.now());
        help.setUpdatedAt(LocalDateTime.now());

        Help updatedHelp = helpRepository.save(help);
        return helpMapper.toResponse(updatedHelp);
    }

    /**
     * 3. Автор подтверждает, что помощь получена
     * ЗДЕСЬ СРАБАТЫВАЕТ ПРАВИЛО ПИРАМИДЫ!
     */
    public HelpResponse confirmHelp(Long helpId) {
        Help help = getHelpById(helpId);

        // 👇 ПРОВЕРКИ
        if (help.getStatus() == HelpStatus.CONFIRMED) {
            throw new RuntimeException("Помощь уже подтверждена");
        }

        if (help.getStatus() == HelpStatus.CANCELLED) {
            throw new RuntimeException("Нельзя подтвердить отмененную помощь");
        }

        if (help.getStatus() != HelpStatus.COMPLETED) {
            throw new RuntimeException("Помощь не в статусе COMPLETED");
        }

        help.setStatus(HelpStatus.CONFIRMED);
        help.setConfirmedAt(LocalDateTime.now());
        help.setUpdatedAt(LocalDateTime.now());

        // Обновляем статус поста
        Post post = help.getPost();
        post.setStatus(PostStatus.COMPLETED);
        postRepository.save(post);

        // Правило пирамиды
        userService.incrementHelpedCount(help.getReceiver().getId());
        userService.userHelpedSomeone(help.getHelper().getId());

        Help updatedHelp = helpRepository.save(help);
        return helpMapper.toResponse(updatedHelp);
    }

    /**
     * 4. Отмена помощи
     */
    public HelpResponse cancelHelp(Long helpId) {
        Help help = getHelpById(helpId);

        validateHelpNotInStatus(help, HelpStatus.CANCELLED, "Помощь уже отменена");
        validateHelpNotInStatus(help, HelpStatus.CONFIRMED, "Нельзя отменить подтвержденную помощь");

        help.setStatus(HelpStatus.CANCELLED);
        help.setUpdatedAt(LocalDateTime.now());

        Post post = help.getPost();
        post.setStatus(PostStatus.OPEN);
        post.setHelper(null);
        postRepository.save(post);

        Help updatedHelp = helpRepository.save(help);
        return helpMapper.toResponse(updatedHelp);
    }

    /**
     * Получить все помощи пользователя (где он помогал)
     */
    @Transactional(readOnly = true)
    public List<HelpResponse> getHelpsByHelper(Long helperId) {
        User helper = userRepository.findById(helperId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        return helpRepository.findByHelper(helper).stream()
                .map(helpMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Получить все помощи пользователя (где ему помогали)
     */
    @Transactional(readOnly = true)
    public List<HelpResponse> getHelpsByReceiver(Long receiverId) {
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        return helpRepository.findByReceiver(receiver).stream()
                .map(helpMapper::toResponse)
                .collect(Collectors.toList());
    }

    private Help getHelpById(Long id) {
        return helpRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Помощь не найдена с id: " + id));
    }

    private void validateHelpStatus(Help help, HelpStatus expectedStatus, String errorMessage) {
        if (help.getStatus() != expectedStatus) {
            throw new RuntimeException(errorMessage);
        }
    }

    private void validateHelpNotInStatus(Help help, HelpStatus forbiddenStatus, String errorMessage) {
        if (help.getStatus() == forbiddenStatus) {
            throw new RuntimeException(errorMessage);
        }
    }
}
