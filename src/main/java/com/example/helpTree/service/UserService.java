package com.example.helpTree.service;

import com.example.helpTree.dto.users.CreateUserRequest;
import com.example.helpTree.dto.users.UpdateUserRequest;
import com.example.helpTree.dto.users.UserDto;
import com.example.helpTree.entity.User;
import com.example.helpTree.enums.UserStatus;
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
public class UserService {

    private final UserRepository userRepository;

    public UserDto createUser(CreateUserRequest request) {
        // Проверяем, нет ли уже такого email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Пользователь с таким email уже существует");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setCity(request.getCity());
        user.setHelpedCount(0);
        user.setDebtCount(0);
        user.setRating(0.0);
        user.setStatus(UserStatus.NEWBIE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        return mapToDto(savedUser);
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        return mapToDto(getUserEntityById(id));
    }

    @Transactional(readOnly = true)
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден с email: " + email));
        return mapToDto(user);
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public UserDto updateUser(Long id, UpdateUserRequest request) {
        User user = getUserEntityById(id);

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null) {
            // Проверяем, не занят ли email другим пользователем
            if (!request.getEmail().equals(user.getEmail()) &&
                    userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email уже используется");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getCity() != null) {
            user.setCity(request.getCity());
        }

        user.setUpdatedAt(LocalDateTime.now());
        User updatedUser = userRepository.save(user);
        return mapToDto(updatedUser);
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("Пользователь не найден с id: " + id);
        }
        userRepository.deleteById(id);
    }

    /**
     * ВЫЗЫВАЕТСЯ, КОГДА КОМУ-ТО ПОМОГЛИ
     * @param receiverId - ID того, КОМУ помогли (получатель помощи)
     *
     * Правило: если человеку помогли, он должен помочь двоим
     */
    public void incrementHelpedCount(Long receiverId) {
        User receiver = getUserEntityById(receiverId);

        // Этому человеку только что помогли, теперь он должен помочь двоим
        receiver.setDebtCount(receiver.getDebtCount() + 2);  // +2 к долгу

        // Обновляем статус
        if (receiver.getStatus() == UserStatus.NEWBIE) {
            receiver.setStatus(UserStatus.HELPER);
        }

        // Если должен помочь - статус DEBTOR
        if (receiver.getDebtCount() > 0) {
            receiver.setStatus(UserStatus.DEBTOR);
        }

        receiver.setUpdatedAt(LocalDateTime.now());
        userRepository.save(receiver);

        System.out.println("✅ Пользователь " + receiver.getName() +
                " теперь должен помочь " + receiver.getDebtCount() + " людям");
    }

    private User getUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден с id: " + id));
    }

    /**
     * ВЫЗЫВАЕТСЯ, КОГДА ПОЛЬЗОВАТЕЛЬ САМ ПОМОГ КОМУ-ТО
     * @param helperId - ID того, КТО помог
     */
    public void userHelpedSomeone(Long helperId) {
        User helper = getUserEntityById(helperId);

        // Увеличиваем счетчик оказанной помощи
        helper.setHelpedCount(helper.getHelpedCount() + 1);

        // Уменьшаем долг (человек выполнил одно обещание)
        if (helper.getDebtCount() > 0) {
            helper.setDebtCount(helper.getDebtCount() - 1);
        }

        // Если долг стал 0, статус ACTIVE
        if (helper.getDebtCount() == 0) {
            helper.setStatus(UserStatus.ACTIVE);
        }

        helper.setUpdatedAt(LocalDateTime.now());
        userRepository.save(helper);

        System.out.println("✅ Пользователь " + helper.getName() +
                " помог кому-то. Осталось помочь: " + helper.getDebtCount());
    }

    /**
     * ПОЛНАЯ ЛОГИКА: один пользователь помогает другому
     * @param helperId - Кто помогает
     * @param receiverId - Кому помогают
     */
    @Transactional
    public void processHelp(Long helperId, Long receiverId) {
        // 1. Тот, кто помогает (helper) - у него уменьшается долг
        userHelpedSomeone(helperId);

        // 2. Тот, кому помогли (receiver) - у него увеличивается долг
        incrementHelpedCount(receiverId);

        System.out.println("✅ Цепочка: пользователь " + helperId +
                " помог пользователю " + receiverId);
    }

    private UserDto mapToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .city(user.getCity())
                .helpedCount(user.getHelpedCount())
                .debtCount(user.getDebtCount())
                .rating(user.getRating())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
