package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UpdateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.dto.users.UserPublicDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.UserMapper;
import org.example.helptreeservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final ImageService imageService;
    private final AchievementService achievementService;

    public UserDto createUser(CreateUserRequest request) {
        return createUserWithRole(request, Role.USER);
    }

    public UserDto createUserWithRole(CreateUserRequest request, Role role) {
        log.info("Создание пользователя {} с ролью {}", request.getEmail(), role);

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Попытка создания пользователя с уже существующим email: {}", request.getEmail());
            throw new ConflictException("Пользователь с таким email уже существует");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        passwordService.validate(request.getPassword());
        user.setPassword(passwordService.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setCity(request.getCity());
        user.setHelpedCount(0);
        user.setDebtCount(0);
        user.setRating(0.0);
        user.setStatus(UserStatus.NEWBIE);
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        achievementService.awardAchievementOnRegistration(savedUser);
        log.info("Пользователь успешно создан с ID: {}, email: {}", savedUser.getId(), savedUser.getEmail());
        return userMapper.toDto(savedUser);
    }

    @Transactional(readOnly = true)
    public User getUserEntityByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с email: " + email));
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        log.info("Запрос пользователя по ID: {}", id);
        try {
            UserDto userDto = userMapper.toDto(getUserEntityById(id));
            log.info("Пользователь с ID {} найден: {}", id, userDto.getEmail());
            return userDto;
        } catch (NotFoundException e) {
            log.warn("Пользователь с ID {} не найден", id);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при поиске пользователя с ID: {}", id, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public UserDto getUserByEmail(String email) {
        log.info("Поиск пользователя по email: {}", email);
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с email: " + email));
            log.info("Пользователь с email {} найден, ID: {}", email, user.getId());
            return userMapper.toDto(user);
        } catch (NotFoundException e) {
            log.warn("Пользователь с email {} не найден", email);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при поиске пользователя с email: {}", email, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        log.info("Запрос списка всех активных пользователей");
        try {
            List<UserDto> users = userRepository.findByDeletedFalse().stream()
                    .map(userMapper::toDto)
                    .collect(Collectors.toList());
            log.info("Получен список пользователей, количество: {}", users.size());
            log.debug("ID пользователей в списке: {}", users.stream().map(UserDto::getId).collect(Collectors.toList()));
            return users;
        } catch (Exception e) {
            log.error("Ошибка при получении списка всех пользователей", e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<UserPublicDto> getAllUsersPublic() {
        return userRepository.findByDeletedFalse().stream()
                .map(userMapper::toPublicDto)
                .collect(Collectors.toList());
    }

    public UserDto updateUser(Long id, UpdateUserRequest request) {
        log.info("Обновление данных пользователя с ID: {}", id);
        log.debug("Данные для обновления: name={}, email={}, phone={}, city={}",
                request.getName(), request.getEmail(), request.getPhone(), request.getCity());

        try {
            User user = getUserEntityById(id);
            log.debug("Найден пользователь для обновления: текущий email={}", user.getEmail());

            boolean changed = false;

            if (request.getName() != null && !request.getName().equals(user.getName())) {
                user.setName(request.getName());
                changed = true;
                log.debug("Изменено имя пользователя на: {}", request.getName());
            }

            if (request.getEmail() != null) {
                if (!request.getEmail().equals(user.getEmail())) {
                    if (userRepository.existsByEmail(request.getEmail())) {
                        log.warn("Попытка обновления на уже существующий email: {}", request.getEmail());
                        throw new ConflictException("Email уже используется");
                    }
                    user.setEmail(request.getEmail());
                    changed = true;
                    log.debug("Изменен email пользователя на: {}", request.getEmail());
                }
            }

            if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
                user.setPhone(request.getPhone());
                changed = true;
                log.debug("Изменен телефон пользователя на: {}", request.getPhone());
            }

            if (request.getCity() != null && !request.getCity().equals(user.getCity())) {
                user.setCity(request.getCity());
                changed = true;
                log.debug("Изменен город пользователя на: {}", request.getCity());
            }

            if (request.getLatitude() != null) {
                user.setLatitude(request.getLatitude());
                changed = true;
            }

            if (request.getLongitude() != null) {
                user.setLongitude(request.getLongitude());
                changed = true;
            }

            if (request.getBirthDate() != null && !request.getBirthDate().equals(user.getBirthDate())) {
                user.setBirthDate(request.getBirthDate());
                changed = true;
                log.debug("Изменена дата рождения пользователя на: {}", request.getBirthDate());
            }

            if (changed) {
                user.setUpdatedAt(LocalDateTime.now());
                User updatedUser = userRepository.save(user);
                log.info("Данные пользователя с ID: {} успешно обновлены", id);
                return userMapper.toDto(updatedUser);
            } else {
                log.info("Нет изменений для обновления пользователя с ID: {}", id);
                return userMapper.toDto(user);
            }

        } catch (NotFoundException e) {
            log.warn("Не удалось обновить пользователя: ID {} не найден", id);
            throw e;
        } catch (ConflictException e) {
            log.warn("Конфликт при обновлении пользователя ID {}: {}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при обновлении пользователя с ID: {}", id, e);
            throw e;
        }
    }

    public void deleteUser(Long id) {
        log.info("Запрос на мягкое удаление пользователя с ID: {}", id);
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

            if (user.getDeleted()) {
                log.warn("Попытка удалить уже удаленного пользователя с ID: {}", id);
                throw new ConflictException("Пользователь уже был удалён");
            }

            user.setDeleted(true);
            user.setDeletedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Пользователь с ID: {} успешно помечен как удаленный", id);
            log.debug("Детали удаленного пользователя: email={}, время удаления={}",
                    user.getEmail(), user.getDeletedAt());

        } catch (NotFoundException e) {
            log.warn("Не удалось удалить пользователя: ID {} не найден", id);
            throw e;
        } catch (ConflictException e) {
            log.warn("Не удалось удалить пользователя: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при удалении пользователя с ID: {}", id, e);
            throw e;
        }
    }

    public void restoreUser(Long id) {
        log.info("Запрос на восстановление пользователя с ID: {}", id);
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

            if (!user.getDeleted()) {
                log.warn("Попытка восстановить не удаленного пользователя с ID: {}", id);
                throw new ConflictException("Пользователь не удалён");
            }

            user.setDeleted(false);
            user.setDeletedAt(null);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("Пользователь с ID: {} успешно восстановлен", id);
            log.debug("Детали восстановленного пользователя: email={}", user.getEmail());

        } catch (NotFoundException e) {
            log.warn("Не удалось восстановить пользователя: ID {} не найден", id);
            throw e;
        } catch (ConflictException e) {
            log.warn("Не удалось восстановить пользователя: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при восстановлении пользователя с ID: {}", id, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public User getUserEntityById(Long id) {
        log.debug("Поиск сущности пользователя по ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

        if (user.getDeleted()) {
            log.debug("Пользователь с ID {} найден, но помечен как удаленный", id);
            throw new NotFoundException("Пользователь не найден с id: " + id);
        }

        log.debug("Сущность пользователя с ID {} успешно найдена", id);
        return user;
    }

    public void incrementHelpedCount(Long receiverId) {
        log.info("Увеличение счетчика долгов для получателя помощи с ID: {}", receiverId);
        try {
            User user = userRepository.findById(receiverId).orElse(null);
            if (user != null) {
                int newDebtCount = user.getDebtCount() + 2;
                userRepository.updateDebtCount(receiverId, 2);
                if (newDebtCount > 2 && user.getDebtStartedAt() == null) {
                    user.setDebtStartedAt(LocalDateTime.now());
                    userRepository.save(user);
                }
            }
            log.info("Счетчик долгов для получателя ID {} успешно обновлен", receiverId);
        } catch (Exception e) {
            log.error("Ошибка при увеличении счетчика долгов для получателя ID: {}", receiverId, e);
            throw e;
        }
    }

    public void userHelpedSomeone(Long helperId) {
        log.info("Обработка помощи от пользователя с ID: {}", helperId);
        try {
            User user = userRepository.findById(helperId).orElse(null);
            userRepository.incrementHelpedCount(helperId);
            userRepository.updateDebtCount(helperId, -1);
            if (user != null && user.getDebtCount() <= 2 && user.getDebtStartedAt() != null) {
                user.setDebtStartedAt(null);
                userRepository.save(user);
            }
            log.info("Данные помощника ID {} успешно обновлены", helperId);
        } catch (Exception e) {
            log.error("Ошибка при обработке помощи от пользователя ID: {}", helperId, e);
            throw e;
        }
    }

    public boolean canReceiveHelp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return user.getDebtCount() <= 5;
    }

    public boolean canCreatePost(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return user.getDebtCount() <= 3;
    }

    @Transactional
    public void processHelp(Long helperId, Long receiverId) {
        log.info("Начало процесса помощи: helperId={}, receiverId={}", helperId, receiverId);
        try {
            log.debug("Шаг 1: Обработка помощи от пользователя {}", helperId);
            userHelpedSomeone(helperId);

            log.debug("Шаг 2: Обновление счетчика получателя {}", receiverId);
            incrementHelpedCount(receiverId);

            log.info("Процесс помощи между helperId={} и receiverId={} успешно завершен",
                    helperId, receiverId);

        } catch (Exception e) {
            log.error("Ошибка в процессе помощи: helperId={}, receiverId={}",
                    helperId, receiverId, e);
            throw e;
        }
    }

    public UserPublicDto getUserPublicById(Long id) {
        log.info("Публичные данные пользователя ID: {}", id);
        User user = getUserEntityById(id);
        return UserPublicDto.builder()
                .id(user.getId())
                .name(user.getName())
                .rating(user.getRating())
                .helpedCount(user.getHelpedCount())
                .debtCount(user.getDebtCount())
                .build();
    }

    public void updateUserRating(Long id, Double rating) {
        log.info("Обновление рейтинга пользователя ID: {} на {}", id, rating);
        try {
            User user = getUserEntityById(id);
            user.setRating(rating);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("Рейтинг пользователя ID {} успешно обновлен", id);
        } catch (Exception e) {
            log.error("Ошибка при обновлении рейтинга пользователя ID: {}", id, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<UserPublicDto> getUsersPublicByIds(List<Long> ids) {
        log.debug("Получение публичных данных для списка пользователей: {}", ids);
        return userRepository.findAllById(ids).stream()
                .filter(u -> !u.getDeleted())
                .map(u -> UserPublicDto.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .rating(u.getRating())
                        .helpedCount(u.getHelpedCount())
                        .debtCount(u.getDebtCount())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void bindTelegramChatId(Long userId, String chatId) {
        log.info("Привязка Telegram Chat ID {} для пользователя {}", chatId, userId);
        User user = getUserEntityById(userId);
        user.setTelegramChatId(chatId);
        userRepository.save(user);
        log.info("Telegram Chat ID {} успешно привязан", chatId);
    }

    @Transactional(readOnly = true)
    public String getTelegramChatId(Long userId) {
        return userRepository.findById(userId)
                .map(User::getTelegramChatId)
                .orElse(null);
    }

    public String uploadAvatar(Long userId, MultipartFile file) {
        log.info("Загрузка аватара для пользователя ID: {}", userId);
        User user = getUserEntityById(userId);
        String avatarUrl = imageService.upload(file);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        log.info("Аватар для пользователя ID {} загружен: {}", userId, avatarUrl);
        return avatarUrl;
    }

    @Transactional
    public void addFavorite(Long userId, Long postId) {
        log.info("Добавление поста {} в избранное для пользователя {}", postId, userId);
        User user = getUserEntityById(userId);
        if (user.getFavoritePostIds() == null) {
            user.setFavoritePostIds(new ArrayList<>());
        }
        if (!user.getFavoritePostIds().contains(postId)) {
            user.getFavoritePostIds().add(postId);
            userRepository.save(user);
            log.info("Пост {} добавлен в избранное пользователя {}", postId, userId);
        }
    }

    @Transactional
    public void removeFavorite(Long userId, Long postId) {
        log.info("Удаление поста {} из избранного для пользователя {}", postId, userId);
        User user = getUserEntityById(userId);
        if (user.getFavoritePostIds() != null && user.getFavoritePostIds().contains(postId)) {
            user.getFavoritePostIds().remove(postId);
            userRepository.save(user);
            log.info("Пост {} удален из избранного пользователя {}", postId, userId);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> getFavorites(Long userId) {
        log.debug("Получение избранных постов для пользователя {}", userId);
        try {
            Long userIdOnly = userRepository.findUserIdOnly(userId);
            if (userIdOnly == null) {
                return new ArrayList<>();
            }
            return userRepository.findFavoritePostIds(userId);
        } catch (Exception e) {
            log.error("Ошибка при получении избранного: {}", e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long postId) {
        User user = userRepository.findById(userId).orElse(null);
        return user != null && user.getFavoritePostIds() != null && user.getFavoritePostIds().contains(postId);
    }

    @Transactional
    public void updateLastLogin(Long userId) {
        log.info("Обновление времени последнего входа для пользователя {}", userId);
        User user = getUserEntityById(userId);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void blockUsersWithDebt() {
        log.info("Запуск проверки блокировки пользователей за долг");
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<User> usersToBlock = userRepository.findUsersWithDebtToBlock(sevenDaysAgo);
        
        for (User user : usersToBlock) {
            if (user.getBlockedAt() == null) {
                user.setBlockedAt(LocalDateTime.now());
                userRepository.save(user);
                log.info("Пользователь {} заблокирован за долг (debtCount: {})", user.getId(), user.getDebtCount());
            }
        }
        
        if (!usersToBlock.isEmpty()) {
            log.info("Заблокировано {} пользователей за долг", usersToBlock.size());
        }
    }

    public boolean isUserBlocked(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        return user.getBlockedAt() != null;
    }
}
