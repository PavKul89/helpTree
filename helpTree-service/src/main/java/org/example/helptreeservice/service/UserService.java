package org.example.helptreeservice.service;

import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UpdateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.dto.users.UserPublicDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.UserMapper;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.repository.UserSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final PostRepository postRepository;
    private final UserRatingStatsRepository ratingStatsRepository;
    private final RatingService ratingService;
    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final ImageService imageService;
    private final AchievementService achievementService;
    private final GeocodingService geocodingService;
    private final AuthSessionService authSessionService;
    private final EconomyConfig economyConfig;
    private final PublicProfileCache publicProfileCache;

    public UserDto createUser(CreateUserRequest request) {
        return createUserWithRole(request, Role.USER);
    }

    public UserDto createUserWithRole(CreateUserRequest request, Role role) {
        log.info("Создание пользователя {} с ролью {}", request.getEmail(), role);

        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.warn("Попытка создания пользователя с уже существующим email: {}", email);
            throw new ConflictException("Пользователь с таким email уже существует");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(email);
        passwordService.validate(request.getPassword());
        user.setPassword(passwordService.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setCity(request.getCity());
        if (request.getCity() != null) {
            geocodingService.cached(request.getCity())
                    .ifPresent(coords -> {
                        user.setLatitude(coords.lat());
                        user.setLongitude(coords.lng());
                    });
            geocodingService.enqueue(request.getCity());
        }
        user.setHelpedCount(0);
        user.setDebtCount(0);
        user.setRating(3.0);
        user.setStatus(UserStatus.NEWBIE);
        user.setRole(role);
        user.setEmailVerified(role == Role.ADMIN);
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
    public Page<UserDto> getAllUsers(Pageable pageable,
                                     String query,
                                     Role role,
                                     UserStatus status,
                                     Boolean blocked) {
        log.info("Запрос страницы пользователей: page={}, size={}, q={}, role={}, status={}, blocked={}",
                pageable.getPageNumber(), pageable.getPageSize(), query, role, status, blocked);
        return userRepository.findAll(
                        UserSpecification.filter(query, role, status, null, blocked, true),
                        pageable)
                .map(userMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<UserPublicDto> getAllUsersPublic(Pageable pageable, String query, String city) {
        log.info("Запрос страницы публичных пользователей: page={}, size={}, q={}, city={}",
                pageable.getPageNumber(), pageable.getPageSize(), query, city);
        return userRepository.findAll(
                        UserSpecification.filter(query, null, null, city, null, false),
                        pageable)
                .map(userMapper::toPublicDto);
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

            if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
                // Changing a login identity requires proof of the current password and
                // control of the new mailbox, so it is only possible via the dedicated
                // email-change flow.
                throw new BadRequestException(
                        "Email нельзя изменить через профиль. Используйте подтверждение нового адреса.");
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
                
                if (request.getLatitude() == null || request.getLongitude() == null) {
                    geocodingService.cached(request.getCity())
                            .ifPresent(coords -> {
                                user.setLatitude(coords.lat());
                                user.setLongitude(coords.lng());
                            });
                    geocodingService.enqueue(request.getCity());
                }
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
                publicProfileCache.evictAfterCommit(id);
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
            User snapshot = userRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

            if (Boolean.TRUE.equals(snapshot.getDeleted())) {
                log.warn("Попытка удалить уже удаленного пользователя с ID: {}", id);
                throw new ConflictException("Пользователь уже был удалён");
            }

            User user;
            if (snapshot.getRole() == Role.ADMIN) {
                List<User> admins = userRepository.findActiveByRoleForUpdate(Role.ADMIN);
                if (admins.size() <= 1) {
                    throw new ConflictException("Нельзя удалить последнего администратора");
                }
                user = admins.stream()
                        .filter(admin -> admin.getId().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new ConflictException("Нельзя удалить последнего администратора"));
            } else {
                user = userRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));
            }

            user.setDeleted(true);
            user.setDeletedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            publicProfileCache.evictAfterCommit(id);

            // A deleted account must lose all active access and refresh tokens now.
            authSessionService.revokeAllSessionsForUser(id, "ACCOUNT_DELETED");

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

    /**
     * Changes a user's role. The last active administrator cannot be demoted, and
     * every existing session is revoked so an old JWT cannot keep the old role.
     */
    @Transactional
    public UserDto changeRole(Long id, Role newRole) {
        if (newRole == null) {
            throw new BadRequestException("Роль обязательна");
        }

        User snapshot = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));
        if (Boolean.TRUE.equals(snapshot.getDeleted())) {
            throw new NotFoundException("Пользователь не найден с id: " + id);
        }

        if (snapshot.getRole() == newRole) {
            return userMapper.toDto(snapshot);
        }

        User user;
        if (snapshot.getRole() == Role.ADMIN) {
            List<User> admins = userRepository.findActiveByRoleForUpdate(Role.ADMIN);
            if (admins.size() <= 1) {
                throw new ConflictException("Нельзя понизить последнего администратора");
            }
            user = admins.stream()
                    .filter(admin -> admin.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new ConflictException("Нельзя понизить последнего администратора"));
        } else {
            user = userRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));
        }

        user.setRole(newRole);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        publicProfileCache.evictAfterCommit(id);

        authSessionService.revokeAllSessionsForUser(id, "ROLE_CHANGED");
        log.info("Роль пользователя {} изменена на {}", id, newRole);
        return userMapper.toDto(user);
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
            publicProfileCache.evictAfterCommit(id);

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

    @Transactional
    public User getUserEntityByIdForUpdate(Long id) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден с id: " + id);
        }

        return user;
    }

    public void incrementHelpedCount(Long receiverId) {
        log.info("Увеличение счетчика долгов для получателя помощи с ID: {}", receiverId);

        // Mutate the managed row instead of issuing a bulk UPDATE: the receiver is
        // already locked by confirmHelp, and a later save of the same entity (for
        // example the coin balance update) would otherwise write back the stale
        // pre-increment debt value and silently lose this increment.
        User receiver = userRepository.findByIdForUpdate(receiverId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + receiverId));
        int debt = receiver.getDebtCount() != null ? receiver.getDebtCount() : 0;
        receiver.setDebtCount(debt + economyConfig.getDebtPerReceivedHelp());
        receiver.setUpdatedAt(LocalDateTime.now());
        userRepository.save(receiver);
        publicProfileCache.evictAfterCommit(receiverId);

        log.info("Счетчик долгов для получателя ID {} успешно обновлен", receiverId);
    }

    public void userHelpedSomeone(Long helperId) {
        log.info("Обработка помощи от пользователя с ID: {}", helperId);
        try {
            User user = userRepository.findByIdForUpdate(helperId).orElse(null);
            if (user != null) {
                user.setHelpedCount(user.getHelpedCount() + 1);
                if (user.getDebtCount() > 0) {
                    user.setDebtCount(user.getDebtCount() - 1);
                }
                
                if (user.getDebtCount() <= economyConfig.getDebtClearThreshold()
                        && user.getDebtStartedAt() != null) {
                    user.setDebtStartedAt(null);
                }
                if (user.getDebtCount() <= economyConfig.getDebtBlockThreshold()
                        && user.getBlockedAt() != null) {
                    user.setBlockedAt(null);
                    user.setBlockedUntil(null);
                    log.info("Пользователь {} автоматически разблокирован (debtCount: {})", helperId, user.getDebtCount());
                }
                userRepository.saveAndFlush(user);
                publicProfileCache.evictAfterCommit(helperId);
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
        if (isUserBlocked(userId)) {
            return false;
        }
        return user.getDebtCount() <= economyConfig.getDebtBlockThreshold();
    }

    public boolean canCreatePost(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (isUserBlocked(userId)) {
            return false;
        }
        return user.getDebtCount() <= 3;
    }

    /**
     * Public profiles are read on almost every page and change rarely. The
     * cache is evicted after commit by every write path (profile, rating,
     * avatar, debt, block); a short TTL in {@code CacheConfig} bounds the
     * window if an eviction is ever missed.
     */
    @Cacheable(value = PublicProfileCache.CACHE_NAME, key = "#id")
    public UserPublicDto getUserPublicById(Long id) {
        log.info("Публичные данные пользователя ID: {}", id);
        User user = getUserEntityById(id);
        return UserPublicDto.builder()
                .id(user.getId())
                .name(user.getName())
                .rating(user.getRating())
                .helpedCount(user.getHelpedCount())
                .debtCount(user.getDebtCount())
                .avatarUrl(imageService.refreshUrl(user.getAvatarUrl()))
                .city(user.getCity())
                .nicknameColor(user.getNicknameColor())
                .build();
    }

    @CacheEvict(value = "userRating", key = "#id")
    public void updateUserRating(Long id, Double rating) {
        log.info("Обновление рейтинга пользователя ID: {} на {}", id, rating);
        try {
            User user = getUserEntityById(id);
            user.setRating(rating);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            publicProfileCache.evictAfterCommit(id);

            // Keep the projection consistent with the manual override and give
            // the scheduled rebuild a full freshness window.
            LocalDateTime now = LocalDateTime.now();
            ratingStatsRepository.findByUserId(id).ifPresent(stats -> {
                stats.setCurrentRating(rating);
                stats.setLastCalculated(now);
                ratingStatsRepository.save(stats);
            });
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
                        .avatarUrl(imageService.refreshUrl(u.getAvatarUrl()))
                        .city(u.getCity())
                        .nicknameColor(u.getNicknameColor())
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
        return getUserEntityById(userId).getTelegramChatId();
    }

    public String uploadAvatar(Long userId, MultipartFile file) {
        log.info("Загрузка аватара для пользователя ID: {}", userId);
        User user = getUserEntityById(userId);
        String avatarUrl = imageService.upload(file);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        publicProfileCache.evictAfterCommit(userId);
        log.info("Аватар для пользователя ID {} загружен: {}", userId, avatarUrl);
        return avatarUrl;
    }

    @Transactional
    public void addFavorite(Long userId, Long postId) {
        log.info("Добавление поста {} в избранное для пользователя {}", postId, userId);

        if (!postRepository.existsByIdAndDeletedFalse(postId)) {
            throw new NotFoundException("Пост не найден");
        }

        User user = getUserEntityByIdForUpdate(userId);
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
        User user = getUserEntityByIdForUpdate(userId);
        if (user.getFavoritePostIds() != null && user.getFavoritePostIds().contains(postId)) {
            user.getFavoritePostIds().remove(postId);
            userRepository.save(user);
            log.info("Пост {} удален из избранного пользователя {}", postId, userId);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> getFavorites(Long userId) {
        log.debug("Получение избранных постов для пользователя {}", userId);
        getUserEntityById(userId);
        return userRepository.findFavoritePostIds(userId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long postId) {
        User user = getUserEntityById(userId);
        return user.getFavoritePostIds() != null && user.getFavoritePostIds().contains(postId);
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
        LocalDateTime now = LocalDateTime.now();
        List<User> usersToBlock = userRepository.findUsersWithDebtToBlock(
                now, economyConfig.getDebtBlockThreshold());
        List<Long> blockedUserIds = new ArrayList<>();
        
        for (User user : usersToBlock) {
            if (user.getBlockedUntil() == null || user.getBlockedUntil().isBefore(LocalDateTime.now())) {
                user.setBlockedAt(LocalDateTime.now());
                user.setBlockedUntil(LocalDateTime.now().plusDays(economyConfig.getDebtBlockDays()));
                userRepository.save(user);
                publicProfileCache.evictAfterCommit(user.getId());
                blockedUserIds.add(user.getId());
                // Debt blocking must immediately invalidate already issued tokens.
                authSessionService.revokeAllSessionsForUser(user.getId(), "ACCOUNT_BLOCKED");
                log.info("Пользователь {} заблокирован за долг на 7 дней (debtCount: {}, blockedUntil: {})", 
                    user.getId(), user.getDebtCount(), user.getBlockedUntil());
            }
        }

        // The block penalty is part of the rating formula: refresh it in the
        // same transaction instead of waiting for the scheduled rebuild.
        ratingService.recalculateUsers(blockedUserIds, "Блокировка за долг");
        
        if (!usersToBlock.isEmpty()) {
            log.info("Заблокировано {} пользователей за долг", usersToBlock.size());
        }
    }

    public boolean isUserBlocked(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        return user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now());
    }

    @Transactional
    public void unblockUser(Long userId) {
        log.info("Разблокировка пользователя {}", userId);
        User user = getUserEntityById(userId);
        user.setBlockedAt(null);
        user.setBlockedUntil(null);
        userRepository.save(user);
        publicProfileCache.evictAfterCommit(userId);
        // Remove the block penalty from the projection immediately.
        ratingService.recalculateUsers(List.of(userId), "Ручная разблокировка");
        log.info("Пользователь {} разблокирован", userId);
    }

    @Transactional
    public void unblockExpiredBlocks() {
        log.info("Запуск авто-разблокировки пользователей с истёкшим сроком");
        LocalDateTime now = LocalDateTime.now();
        // Read the ids before the bulk update: loading entities here would leave
        // stale blocked values in the persistence context for the recalculation.
        List<Long> unblockedIds = userRepository.findIdsWithExpiredBlocks(now);
        int unblocked = userRepository.unblockExpiredUsers(now);
        unblockedIds.forEach(publicProfileCache::evictAfterCommit);
        ratingService.recalculateUsers(unblockedIds, "Автоматическая разблокировка");
        if (unblocked > 0) {
            log.info("Авто-разблокировано {} пользователей", unblocked);
        }
    }
}
