package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.admin.AdminAuditLogDto;
import org.example.helptreeservice.dto.admin.AdminBulkResultDto;
import org.example.helptreeservice.dto.admin.AdminBulkUsersRequest;
import org.example.helptreeservice.dto.admin.AdminCreateUserRequest;
import org.example.helptreeservice.dto.admin.AdminInviteUserRequest;
import org.example.helptreeservice.dto.admin.AdminSessionDto;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.UserMapper;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administrative user lifecycle. Every mutating method requires a reason,
 * delegates the invariant checks (last administrator, session revocation) to
 * {@link UserService}, and writes an append-only audit entry in the same
 * transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final UserMapper userMapper;
    private final AccountSecurityService accountSecurityService;
    private final AdminAuditService adminAuditService;
    private final UserDataExportService userDataExportService;
    private final AuthSessionService authSessionService;

    @Transactional(readOnly = true)
    public Page<UserDto> search(Pageable pageable,
                                String query,
                                Role role,
                                UserStatus status,
                                Boolean blocked,
                                Boolean deleted) {
        return userService.getAllUsers(pageable, query, role, status, blocked, deleted);
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return userMapper.toDto(userService.getUserEntityByIdIncludingDeleted(id));
    }

    @Transactional
    public UserDto createWithPassword(AdminCreateUserRequest request, Long adminId) {
        String reason = requireReason(request.getReason());
        Role role = request.getRole() != null ? request.getRole() : Role.USER;

        CreateUserRequest createRequest = toCreateRequest(
                request.getName(), request.getEmail(), request.getPassword(),
                request.getPhone(), request.getCity());
        UserDto created = userService.createUserWithRole(createRequest, role);

        adminAuditService.record(adminId, AdminAuditAction.USER_CREATED,
                AdminAuditService.ENTITY_USER, created.getId(),
                null, afterState(created), reason);
        log.info("Администратор {} создал пользователя {} с ролью {}", adminId, created.getId(), role);
        return created;
    }

    /**
     * Creates a locked account and emails a single-use password setup link. If
     * mail delivery fails, the transaction rolls back so no inaccessible account
     * is left behind.
     */
    @Transactional
    public UserDto invite(AdminInviteUserRequest request, Long adminId) {
        String reason = requireReason(request.getReason());
        Role role = request.getRole() != null ? request.getRole() : Role.USER;
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Пользователь с таким email уже существует");
        }

        CreateUserRequest createRequest = toCreateRequest(
                request.getName(), normalizedEmail, generateInvitationPassword(),
                request.getPhone(), request.getCity());
        UserDto created = userService.createUserWithRole(createRequest, role);

        User user = userRepository.findById(created.getId())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        accountSecurityService.sendAccountInvitation(user);

        adminAuditService.record(adminId, AdminAuditAction.USER_INVITED,
                AdminAuditService.ENTITY_USER, created.getId(),
                null, afterState(created), reason);
        log.info("Администратор {} пригласил пользователя {} с ролью {}", adminId, created.getId(), role);
        return created;
    }

    @Transactional
    public UserDto changeRole(Long id, Role newRole, String reason, Long adminId) {
        String auditedReason = requireReason(reason);
        if (newRole == null) {
            throw new BadRequestException("Роль обязательна");
        }

        User snapshot = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));
        if (Boolean.TRUE.equals(snapshot.getDeleted())) {
            throw new NotFoundException("Пользователь не найден с id: " + id);
        }

        Role previousRole = snapshot.getRole();
        UserDto updated = userService.changeRole(id, newRole);

        if (previousRole != newRole) {
            adminAuditService.record(adminId, AdminAuditAction.USER_ROLE_CHANGED,
                    AdminAuditService.ENTITY_USER, id,
                    Map.of("role", previousRole.name()),
                    Map.of("role", newRole.name()),
                    auditedReason);
            log.info("Администратор {} изменил роль пользователя {}: {} -> {}",
                    adminId, id, previousRole, newRole);
        }
        return updated;
    }

    @Transactional
    public void delete(Long id, String reason, Long adminId) {
        String auditedReason = requireReason(reason);
        User snapshot = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

        Map<String, Object> before = identityState(snapshot);
        userService.deleteUser(id);

        adminAuditService.record(adminId, AdminAuditAction.USER_DELETED,
                AdminAuditService.ENTITY_USER, id,
                before, Map.of("deleted", true), auditedReason);
        log.info("Администратор {} удалил пользователя {}", adminId, id);
    }

    @Transactional
    public void restore(Long id, String reason, Long adminId) {
        String auditedReason = requireReason(reason);
        userService.restoreUser(id);

        adminAuditService.record(adminId, AdminAuditAction.USER_RESTORED,
                AdminAuditService.ENTITY_USER, id,
                Map.of("deleted", true), Map.of("deleted", false), auditedReason);
        log.info("Администратор {} восстановил пользователя {}", adminId, id);
    }

    @Transactional
    public void anonymize(Long id, String reason, String confirmEmail, Long adminId) {
        String auditedReason = requireReason(reason);
        User snapshot = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));

        if (confirmEmail != null && !confirmEmail.isBlank()
                && !confirmEmail.trim().equalsIgnoreCase(snapshot.getEmail())) {
            throw new BadRequestException("Email подтверждения не совпадает с аккаунтом");
        }

        Map<String, Object> before = identityState(snapshot);
        userService.anonymizeUser(id, auditedReason);

        adminAuditService.record(adminId, AdminAuditAction.USER_ANONYMIZED,
                AdminAuditService.ENTITY_USER, id,
                before, Map.of("anonymized", true), auditedReason);
        log.info("Администратор {} анонимизировал пользователя {}", adminId, id);
    }

    /**
     * Export is a read, but it is still a sensitive administrative action, so a
     * reason and an audit entry are mandatory.
     */
    @Transactional
    public Map<String, Object> export(Long id, String reason, Long adminId) {
        String auditedReason = requireReason(reason);
        User user = userService.getUserEntityByIdIncludingDeleted(id);
        Map<String, Object> document = userDataExportService.export(user);

        adminAuditService.record(adminId, AdminAuditAction.USER_DATA_EXPORTED,
                AdminAuditService.ENTITY_USER, id,
                null, Map.of("format", "json"), auditedReason);
        log.info("Администратор {} экспортировал данные пользователя {}", adminId, id);
        return document;
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLogDto> auditHistory(Long id, Pageable pageable) {
        // Fail with a clear 404 instead of returning an empty page for a typo.
        userService.getUserEntityByIdIncludingDeleted(id);
        return adminAuditService.history(AdminAuditService.ENTITY_USER, id, pageable);
    }

    @Transactional
    public void changeRating(Long id, Double rating, String reason, Long adminId) {
        String auditedReason = requireReason(reason);
        if (rating == null || rating < 0 || rating > 5) {
            throw new BadRequestException("Рейтинг должен быть от 0 до 5");
        }

        User snapshot = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + id));
        if (Boolean.TRUE.equals(snapshot.getDeleted())) {
            throw new NotFoundException("Пользователь не найден с id: " + id);
        }

        Double previous = snapshot.getRating();
        userService.updateUserRating(id, rating);

        adminAuditService.record(adminId, AdminAuditAction.RATING_CHANGED,
                AdminAuditService.ENTITY_USER, id,
                Map.of("rating", previous == null ? "" : previous.toString()),
                Map.of("rating", rating.toString()),
                auditedReason);
        log.info("Администратор {} изменил рейтинг пользователя {}: {} -> {}",
                adminId, id, previous, rating);
    }

    @Transactional(readOnly = true)
    public List<AdminSessionDto> sessions(Long id) {
        userService.getUserEntityByIdIncludingDeleted(id);
        return authSessionService.listActiveSessions(id, null).stream()
                .map(session -> AdminSessionDto.builder()
                        .id(session.id())
                        .createdAt(session.createdAt())
                        .lastUsedAt(session.lastUsedAt())
                        .expiresAt(session.expiresAt())
                        .ipAddress(session.ipAddress())
                        .userAgent(session.userAgent())
                        .deviceName(session.deviceName())
                        .current(session.current())
                        .build())
                .toList();
    }

    @Transactional
    public void revokeSession(Long userId, UUID sessionId, String reason, Long adminId) {
        String auditedReason = requireReason(reason);
        authSessionService.revokeSession(userId, sessionId, auditedReason);

        adminAuditService.record(adminId, AdminAuditAction.SESSION_REVOKED,
                AdminAuditService.ENTITY_SESSION, userId,
                Map.of("sessionId", sessionId.toString(), "active", true),
                Map.of("sessionId", sessionId.toString(), "active", false),
                auditedReason);
        log.info("Администратор {} отозвал сессию {} пользователя {}", adminId, sessionId, userId);
    }

    @Transactional
    public void revokeAllSessions(Long userId, String reason, Long adminId) {
        String auditedReason = requireReason(reason);
        authSessionService.revokeAllSessionsForUser(userId, auditedReason);

        adminAuditService.record(adminId, AdminAuditAction.SESSION_REVOKED_ALL,
                AdminAuditService.ENTITY_SESSION, userId,
                null, Map.of("allSessionsRevoked", true), auditedReason);
        log.info("Администратор {} отозвал все сессии пользователя {}", adminId, userId);
    }

    @Transactional
    public AdminBulkResultDto bulk(AdminBulkUsersRequest request, Long adminId) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            throw new BadRequestException("Список пользователей не может быть пустым");
        }
        String operation = request.getAction() == null
                ? ""
                : request.getAction().trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("DELETE", "RESTORE", "CHANGE_ROLE").contains(operation)) {
            throw new BadRequestException("Поддерживаются операции DELETE, RESTORE и CHANGE_ROLE");
        }
        String auditedReason = requireReason(request.getReason());
        Role role = null;
        if ("CHANGE_ROLE".equals(operation)) {
            if (request.getRole() == null || request.getRole().isBlank()) {
                throw new BadRequestException("Для операции CHANGE_ROLE нужна роль");
            }
            try {
                role = Role.valueOf(request.getRole().trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Неизвестная роль: " + request.getRole());
            }
        }

        List<AdminBulkResultDto.Item> items = new java.util.ArrayList<>();
        int succeeded = 0;
        for (Long id : request.getIds()) {
            try {
                switch (operation) {
                    case "DELETE" -> delete(id, auditedReason, adminId);
                    case "RESTORE" -> restore(id, auditedReason, adminId);
                    case "CHANGE_ROLE" -> changeRole(id, role, auditedReason, adminId);
                    default -> throw new BadRequestException("Неподдерживаемая операция");
                }
                succeeded++;
                items.add(AdminBulkResultDto.Item.builder().id(id).success(true).build());
            } catch (RuntimeException ex) {
                items.add(AdminBulkResultDto.Item.builder()
                        .id(id).success(false).error(ex.getMessage()).build());
            }
        }

        adminAuditService.record(adminId, AdminAuditAction.BULK_OPERATION,
                AdminAuditService.ENTITY_USER, null, null,
                Map.of("operation", operation, "requested", request.getIds().size(),
                        "succeeded", succeeded),
                auditedReason);

        return AdminBulkResultDto.builder()
                .action(operation)
                .requested(request.getIds().size())
                .succeeded(succeeded)
                .failed(request.getIds().size() - succeeded)
                .items(items)
                .build();
    }

    private CreateUserRequest toCreateRequest(String name,
                                              String email,
                                              String password,
                                              String phone,
                                              String city) {
        CreateUserRequest request = new CreateUserRequest();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setPhone(phone);
        request.setCity(city);
        return request;
    }

    private String generateInvitationPassword() {
        // Satisfies the password policy (upper, lower, digit) without ever being
        // shown to the administrator or written to logs.
        return "Invite" + UUID.randomUUID().toString().replace("-", "") + "1aA";
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Причина административного действия обязательна");
        }
        return reason.trim();
    }

    private Map<String, Object> afterState(UserDto user) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", user.getId());
        state.put("email", user.getEmail());
        state.put("role", user.getRole() != null ? user.getRole().name() : null);
        state.put("status", user.getStatus() != null ? user.getStatus().name() : null);
        state.put("deleted", user.getDeleted());
        return state;
    }

    private Map<String, Object> identityState(User user) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", user.getId());
        state.put("role", user.getRole() != null ? user.getRole().name() : null);
        state.put("deleted", user.getDeleted());
        return state;
    }
}
