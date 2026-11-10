package org.example.helptreeservice.service;

import org.example.helptreeservice.config.AuthConfig;
import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UpdateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.dto.users.UserPublicDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserPrivacySettings;
import org.example.helptreeservice.enums.ImagePurpose;
import org.example.helptreeservice.enums.NotificationType;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.UserMapper;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserPrivacySettingsRepository;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.repository.UserSpecification;
import org.example.helptreeservice.service.notification.NotificationCommand;
import org.example.helptreeservice.service.notification.NotificationService;
import org.example.helptreeservice.util.SensitiveDataMasker;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final NotificationService notificationService;
    private final UserPrivacySettingsRepository privacySettingsRepository;
    private final AuthConfig authConfig;

    /**
     * The only privacy facts a public projection needs. Cached separately from
     * the full profile so a post page can hide the author's city without an
     * extra query per row.
     */
    public record PublicPrivacyView(boolean showCity, boolean showPhone, boolean showOnlineStatus) {
    }

    public UserDto createUser(CreateUserRequest request) {
        return createUserWithRole(request, Role.USER);
    }

    public UserDto createUserWithRole(CreateUserRequest request, Role role) {
        log.info("Создание пользователя {} с ролью {}", SensitiveDataMasker.maskEmail(request.getEmail()), role);

        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.warn("Попытка создания пользователя с уже существующим email: {}",
                    SensitiveDataMasker.maskEmail(email));
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
        log.info("Пользователь успешно создан с ID: {}, email: {}", savedUser.getId(),
                SensitiveDataMasker.maskEmail(savedUser.getEmail()));
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
            log.info("Пользователь с ID {} найден: {}", id,
                    SensitiveDataMasker.maskEmail(userDto.getEmail()));
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
        String maskedEmail = SensitiveDataMasker.maskEmail(email);
        log.info("Поиск пользователя по email: {}", maskedEmail);
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с email: " + email));
            log.info("Пользователь с email {} найден, ID: {}", maskedEmail, user.getId());
            return userMapper.toDto(user);
        } catch (NotFoundException e) {
            log.warn("Пользователь с email {} не найден", maskedEmail);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при поиске пользователя с email: {}", maskedEmail, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<UserDto> getAllUsers(Pageable pageable,
                                     String query,
                                     Role role,
                                     UserStatus status,
                                     Boolean blocked) {
        return getAllUsers(pageable, query, role, status, blocked, false);
    }

    /**
     * Admin directory with an explicit deletion scope. Active-user callers keep
     * the five-argument overload above; the deleted-accounts view passes
     * {@code deleted=true}.
     */
    @Transactional(readOnly = true)
    public Page<UserDto> getAllUsers(Pageable pageable,
                                     String query,
                                     Role role,
                                     UserStatus status,
                                     Boolean blocked,
                                     Boolean deleted) {
        log.info("Запрос страницы пользователей: page={}, size={}, q={}, role={}, status={}, blocked={}, deleted={}",
                pageable.getPageNumber(), pageable.getPageSize(), query, role, status, blocked, deleted);
        return userRepository.findAll(
                        UserSpecification.filter(query, role, status, null, blocked, true, deleted),
                        pageable)
                .map(userMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<UserPublicDto> getAllUsersPublic(Pageable pageable, String query, String city) {
        return getAllUsersPublic(pageable, query, city, null);
    }

    /**
     * Public directory with viewer-specific block filtering. A block in the
     * PROFILE scope is bidirectional, so users connected to the viewer by an
     * active block disappear from both sides of the directory.
     */
    @Transactional(readOnly = true)
    public Page<UserPublicDto> getAllUsersPublic(Pageable pageable, String query, String city, Long viewerId) {
        log.info("Запрос страницы публичных пользователей: page={}, size={}, q={}, city={}, viewer={}",
                pageable.getPageNumber(), pageable.getPageSize(), query, city, viewerId);
        Page<User> page = userRepository.findAll(
                UserSpecification.filter(query, null, null, city, null, false)
                        .and(UserSpecification.excludeProfileBlocks(viewerId)),
                pageable);
        Map<Long, UserPrivacySettings> settings = privacySettingsByIds(page.getContent());
        LocalDateTime now = LocalDateTime.now();
        return page.map(user -> toPublicDto(user, settings.get(user.getId()), now));
    }

    private Map<Long, UserPrivacySettings> privacySettingsByIds(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, UserPrivacySettings> result = new HashMap<>();
        privacySettingsRepository.findAllById(ids)
                .forEach(settings -> result.put(settings.getUserId(), settings));
        return result;
    }

    public UserDto updateUser(Long id, UpdateUserRequest request) {
        log.info("Обновление данных пользователя с ID: {}", id);
        log.debug("Данные для обновления: name={}, email={}, phone={}, city={}",
                request.getName() != null,
                SensitiveDataMasker.maskEmail(request.getEmail()),
                SensitiveDataMasker.maskPhone(request.getPhone()),
                request.getCity());

        try {
            User user = getUserEntityById(id);
            log.debug("Найден пользователь для обновления: текущий email={}",
                    SensitiveDataMasker.maskEmail(user.getEmail()));

            boolean changed = false;

            if (request.getName() != null && !request.getName().equals(user.getName())) {
                user.setName(request.getName());
                changed = true;
                log.debug("Изменено имя пользователя");
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
                log.debug("Изменен телефон пользователя на: {}",
                        SensitiveDataMasker.maskPhone(request.getPhone()));
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
                    SensitiveDataMasker.maskEmail(user.getEmail()), user.getDeletedAt());

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

            if (user.getAnonymizedAt() != null) {
                throw new ConflictException("Анонимизированный аккаунт нельзя восстановить");
            }

            user.setDeleted(false);
            user.setDeletedAt(null);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            publicProfileCache.evictAfterCommit(id);

            log.info("Пользователь с ID: {} успешно восстановлен", id);
            log.debug("Детали восстановленного пользователя: email={}",
                    SensitiveDataMasker.maskEmail(user.getEmail()));

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
    public User getUserEntityByIdIncludingDeleted(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));
    }

    /**
     * Irreversibly removes personal data while keeping the row so historical
     * helps, reviews and audit entries remain referentially valid. The account is
     * also soft-deleted and every session is revoked in the same transaction.
     */
    @Transactional
    public User anonymizeUser(Long id, String reason) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

        if (user.getAnonymizedAt() != null) {
            throw new ConflictException("Аккаунт уже анонимизирован");
        }

        // The last active administrator must remain usable even when the caller
        // is an admin or the account owner. A deleted admin is not active.
        if (user.getRole() == Role.ADMIN && !Boolean.TRUE.equals(user.getDeleted())) {
            List<User> admins = userRepository.findActiveByRoleForUpdate(Role.ADMIN);
            if (admins.size() <= 1) {
                throw new ConflictException("Нельзя анонимизировать последнего администратора");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String anonymousEmail = "anonymized+" + id + "@anon.helptree.local";

        user.setName("Анонимизированный пользователь #" + id);
        user.setEmail(anonymousEmail);
        user.setPassword(passwordService.encode(java.util.UUID.randomUUID() + "Aa1"));
        user.setPhone(null);
        user.setCity(null);
        user.setLatitude(null);
        user.setLongitude(null);
        user.setBirthDate(null);
        user.setAvatarUrl(null);
        user.setTelegramChatId(null);
        user.setNicknameColor(null);
        user.setEmailVerified(false);
        user.setBlockedAt(null);
        user.setBlockedUntil(null);
        user.setDebtStartedAt(null);
        user.setDebtCount(0);
        user.setHelpCoins(0L);
        user.setVipUntil(null);
        user.setFavoritePostIds(new ArrayList<>());
        user.setDeleted(true);
        if (user.getDeletedAt() == null) {
            user.setDeletedAt(now);
        }
        user.setAnonymizedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        publicProfileCache.evictAfterCommit(id);

        authSessionService.revokeAllSessionsForUser(id, "ACCOUNT_ANONYMIZED");

        log.info("Аккаунт {} анонимизирован (reason: {})", id, reason != null ? reason : "—");
        return user;
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
        UserPrivacySettings settings = privacySettingsRepository.findById(id).orElse(null);
        return toPublicDto(user, settings, LocalDateTime.now());
    }

    /**
     * Cached privacy projection for callers that already have a user row (post
     * feeds). Deriving it from the same defaults as the profile keeps the
     * visibility rule in one place.
     */
    @Cacheable(value = PublicProfileCache.PRIVACY_CACHE_NAME, key = "#userId")
    @Transactional(readOnly = true)
    public PublicPrivacyView publicPrivacyView(Long userId) {
        UserPrivacySettings settings = privacySettingsRepository.findById(userId).orElse(null);
        return new PublicPrivacyView(
                settings == null || !Boolean.FALSE.equals(settings.getShowCity()),
                settings != null && Boolean.TRUE.equals(settings.getShowPhone()),
                settings == null || !Boolean.FALSE.equals(settings.getShowOnlineStatus()));
    }

    private UserPublicDto toPublicDto(User user,
                                      UserPrivacySettings settings,
                                      LocalDateTime now) {
        boolean showCity = settings == null || !Boolean.FALSE.equals(settings.getShowCity());
        boolean showPhone = settings != null && Boolean.TRUE.equals(settings.getShowPhone());
        boolean showOnline = settings == null || !Boolean.FALSE.equals(settings.getShowOnlineStatus());

        return UserPublicDto.builder()
                .id(user.getId())
                .name(user.getName())
                .rating(user.getRating())
                .helpedCount(user.getHelpedCount())
                .debtCount(user.getDebtCount())
                .avatarUrl(imageService.refreshUrl(user.getAvatarUrl()))
                .city(showCity ? user.getCity() : null)
                .phone(showPhone ? user.getPhone() : null)
                .online(showOnline ? isOnline(user.getLastSeenAt(), now) : null)
                .nicknameColor(user.getNicknameColor())
                .build();
    }

    private boolean isOnline(LocalDateTime lastSeenAt, LocalDateTime now) {
        return lastSeenAt != null
                && !lastSeenAt.isBefore(now.minusMinutes(
                        Math.max(1, authConfig.getOnlineWindowMinutes())));
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
        List<User> users = userRepository.findAllById(ids).stream()
                .filter(u -> !u.getDeleted())
                .toList();
        Map<Long, UserPrivacySettings> settings = privacySettingsByIds(users);
        LocalDateTime now = LocalDateTime.now();
        return users.stream()
                .map(u -> toPublicDto(u, settings.get(u.getId()), now))
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
        User user = getUserEntityByIdForUpdate(userId);
        String previousAvatar = user.getAvatarUrl();

        String avatarUrl = imageService.upload(file, userId, ImagePurpose.AVATAR);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        publicProfileCache.evictAfterCommit(userId);

        // The old object is released after the new avatar is assigned, so a
        // shared post image is never removed just because the avatar changed.
        if (previousAvatar != null && !previousAvatar.equals(avatarUrl)) {
            imageService.releaseReplacedAvatar(userId, previousAvatar);
        }

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
                notifyAccountBlocked(user, "задолженность по HelpCoin");
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
        boolean wasBlocked = user.getBlockedAt() != null || user.getBlockedUntil() != null;
        LocalDateTime previousBlockedUntil = user.getBlockedUntil();
        user.setBlockedAt(null);
        user.setBlockedUntil(null);
        userRepository.save(user);
        publicProfileCache.evictAfterCommit(userId);
        // Remove the block penalty from the projection immediately.
        ratingService.recalculateUsers(List.of(userId), "Ручная разблокировка");
        if (wasBlocked) {
            notifyAccountUnblocked(user.getId(), "блокировка снята администратором",
                    "manual:" + userId + ":" + previousBlockedUntil);
        }
        log.info("Пользователь {} разблокирован", userId);
    }

    @Transactional
    public void unblockExpiredBlocks() {
        log.info("Запуск авто-разблокировки пользователей с истёкшим сроком");
        LocalDateTime now = LocalDateTime.now();
        // Read the ids before the bulk update: loading entities here would leave
        // stale blocked values in the persistence context for the recalculation.
        List<Long> unblockedIds = userRepository.findIdsWithExpiredBlocks(now);
        if (unblockedIds.isEmpty()) {
            return;
        }

        // Snapshot the deadline before the bulk UPDATE clears it; the value is
        // part of the dedupe key so the same expiry notifies exactly once.
        Map<Long, LocalDateTime> expiries = new LinkedHashMap<>();
        for (User user : userRepository.findAllById(unblockedIds)) {
            expiries.put(user.getId(), user.getBlockedUntil());
        }

        int unblocked = userRepository.unblockExpiredUsers(now);
        unblockedIds.forEach(publicProfileCache::evictAfterCommit);
        ratingService.recalculateUsers(unblockedIds, "Автоматическая разблокировка");

        for (Long userId : unblockedIds) {
            notifyAccountUnblocked(userId, "срок блокировки истёк",
                    "expired:" + userId + ":" + expiries.get(userId));
        }
        if (unblocked > 0) {
            log.info("Авто-разблокировано {} пользователей", unblocked);
        }
    }

    private void notifyAccountBlocked(User user, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.getId());
        payload.put("reason", reason);
        payload.put("blockedAt", user.getBlockedAt());
        payload.put("blockedUntil", user.getBlockedUntil());
        notificationService.notify(NotificationCommand.of(
                user.getId(),
                NotificationType.ACCOUNT_BLOCKED,
                "Аккаунт заблокирован",
                "Ваш аккаунт заблокирован. Причина: " + reason + "."
                        + (user.getBlockedUntil() == null ? ""
                        : " Блокировка действует до " + user.getBlockedUntil() + "."),
                payload,
                "account:blocked:" + user.getId() + ":" + user.getBlockedUntil()));
    }

    private void notifyAccountUnblocked(Long userId, String reason, String dedupeSuffix) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("reason", reason);
        notificationService.notify(NotificationCommand.of(
                userId,
                NotificationType.ACCOUNT_UNBLOCKED,
                "Аккаунт разблокирован",
                "Ваш аккаунт снова активен: " + reason + ".",
                payload,
                "account:unblocked:" + dedupeSuffix));
    }
}
