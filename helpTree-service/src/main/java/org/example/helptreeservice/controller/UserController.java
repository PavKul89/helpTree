package org.example.helptreeservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.users.BlockUserRequest;
import org.example.helptreeservice.dto.users.BlockedUserResponse;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UpdateUserRequest;
import org.example.helptreeservice.dto.users.UpdateRoleRequest;
import org.example.helptreeservice.dto.users.UserBlockResponse;
import org.example.helptreeservice.dto.users.UserBlockStatusResponse;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.dto.users.UserPublicDto;
import org.example.helptreeservice.dto.wallet.CoinTransactionDto;
import org.example.helptreeservice.dto.wallet.WalletDto;
import org.example.helptreeservice.dto.wallet.WalletOperationResult;
import org.example.helptreeservice.dto.wallet.WalletPricingDto;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.TransactionDirection;
import org.example.helptreeservice.enums.TransactionType;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.AuthorizationService.UserContext;
import org.example.helptreeservice.service.UserBlockService;
import org.example.helptreeservice.service.UserService;
import org.example.helptreeservice.service.WalletService;
import org.example.helptreeservice.util.PageRequests;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthorizationService authService;
    private final WalletService walletService;
    private final PaginationConfig paginationConfig;
    private final EconomyConfig economyConfig;
    private final UserBlockService userBlockService;

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может создавать пользователей");
        }
        UserDto created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserDto>> getAllUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может просматривать всех пользователей");
        }
        Pageable pageable = resolveDirectoryPageable(page, size, sort, direction,
                java.util.Set.of("id", "name", "email", "rating", "helpedCount", "createdAt"));
        Page<UserDto> users = userService.getAllUsers(pageable, q, role, status, blocked);
        return ResponseEntity.ok(PageResponse.from(users));
    }

    @GetMapping("/public")
    public ResponseEntity<PageResponse<UserPublicDto>> getAllUsersPublic(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "rating") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        Pageable pageable = resolveDirectoryPageable(page, size, sort, direction,
                java.util.Set.of("id", "name", "rating", "helpedCount", "createdAt"));
        UserContext viewer = authService.getCurrentUser();
        Page<UserPublicDto> users = userService.getAllUsersPublic(
                pageable, q, city, viewer != null ? viewer.getUserId() : null);
        return ResponseEntity.ok(PageResponse.from(users));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        UserContext user = authService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Вы не авторизованы");
        }
        return ResponseEntity.ok(userService.getUserById(user.getUserId()));
    }

    @GetMapping("/{id}/public")
    public ResponseEntity<UserPublicDto> getUserPublic(@PathVariable Long id) {
        UserContext viewer = authService.getCurrentUser();
        if (viewer != null && !viewer.getUserId().equals(id)) {
            userBlockService.assertInteractionAllowed(viewer.getUserId(), id, UserBlockScope.PROFILE,
                    "Профиль недоступен: пользователь ограничил просмотр");
        }
        return ResponseEntity.ok(userService.getUserPublicById(id));
    }

    /**
     * Creates or updates the caller's block against {@code id}. The body is
     * optional: an absent body is a permanent ALL block. Repeated calls are
     * idempotent and update the existing pair instead of failing.
     */
    @PostMapping("/{id}/block")
    public ResponseEntity<UserBlockResponse> blockUser(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) BlockUserRequest request) {
        UserContext viewer = requireCurrentUser();
        return ResponseEntity.ok(userBlockService.blockUser(viewer.getUserId(), id, request));
    }

    /** Removes the caller's block. A missing block is not an error (idempotent). */
    @DeleteMapping("/{id}/block")
    public ResponseEntity<Void> unblockUser(@PathVariable Long id) {
        UserContext viewer = requireCurrentUser();
        userBlockService.unblockUser(viewer.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Users the caller currently blocks. Expired rows are not returned. */
    @GetMapping("/me/blocked")
    public ResponseEntity<List<BlockedUserResponse>> getMyBlockedUsers() {
        UserContext viewer = requireCurrentUser();
        return ResponseEntity.ok(userBlockService.getBlockedUsers(viewer.getUserId()));
    }

    /** Viewer-specific block state used to render the profile controls. */
    @GetMapping("/{id}/block-status")
    public ResponseEntity<UserBlockStatusResponse> getBlockStatus(@PathVariable Long id) {
        UserContext viewer = requireCurrentUser();
        return ResponseEntity.ok(userBlockService.getBlockStatus(viewer.getUserId(), id));
    }

    @PostMapping("/public/batch")
    public ResponseEntity<List<UserPublicDto>> getUsersPublicBatch(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        if (ids.size() > 100) {
            throw new BadRequestException("Максимальный размер пакетного запроса — 100 ID");
        }
        return ResponseEntity.ok(userService.getUsersPublicByIds(ids));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        requireOwnerOrAdmin(id, "Вы можете просматривать только свой профиль");
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserDto> getUserByEmail(@PathVariable String email) {
        UserContext user = authService.getCurrentUser();
        if (user == null || (!"ADMIN".equals(user.getRole()) && !email.equals(user.getEmail()))) {
            throw new ForbiddenException("Вы можете искать только свой профиль");
        }
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        requireOwnerOrAdmin(id, "Вы можете редактировать только свой профиль");
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PutMapping("/{id}/rating")
    public ResponseEntity<Void> updateUserRating(
            @PathVariable Long id,
            @RequestParam Double rating) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может изменять рейтинг");
        }
        log.info("Обновление рейтинга пользователя ID: {}, новый рейтинг: {}", id, rating);
        userService.updateUserRating(id, rating);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserDto> changeUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может изменять роли");
        }
        return ResponseEntity.ok(userService.changeRole(id, request.getRole()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        requireOwnerOrAdmin(id, "Вы можете удалить только свой аккаунт");
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restoreUser(@PathVariable Long id) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может восстанавливать пользователей");
        }
        userService.restoreUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/telegram-chat-id")
    public ResponseEntity<String> getTelegramChatId(@PathVariable Long id) {
        requireOwnerOrAdmin(id, "Telegram chat ID доступен только владельцу аккаунта");
        String chatId = userService.getTelegramChatId(id);
        return ResponseEntity.ok(chatId != null ? chatId : "");
    }

    @PutMapping("/{id}/telegram")
    public ResponseEntity<Void> bindTelegram(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        requireOwnerOrAdmin(id, "Вы можете привязать Telegram только к своему аккаунту");
        String chatId = body.get("chatId");
        if (chatId == null || chatId.isBlank()) {
            throw new BadRequestException("chatId обязателен");
        }
        userService.bindTelegramChatId(id, chatId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        requireOwnerOrAdmin(id, "Вы можете загрузить аватар только для своего профиля");
        String avatarUrl = userService.uploadAvatar(id, file);
        return ResponseEntity.ok(Map.of("url", avatarUrl));
    }

    @PostMapping("/{id}/favorites/{postId}")
    public ResponseEntity<Void> addFavorite(@PathVariable Long id, @PathVariable Long postId) {
        requireOwnerOrAdmin(id, "Вы можете добавлять в избранное только для своего профиля");
        userService.addFavorite(id, postId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/favorites/{postId}")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long id, @PathVariable Long postId) {
        requireOwnerOrAdmin(id, "Вы можете удалять из избранного только для своего профиля");
        userService.removeFavorite(id, postId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/favorites")
    public ResponseEntity<List<Long>> getFavorites(@PathVariable Long id) {
        requireOwnerOrAdmin(id, "Вы можете просматривать избранное только своего профиля");
        List<Long> favorites = userService.getFavorites(id);
        return ResponseEntity.ok(favorites != null ? favorites : java.util.Collections.emptyList());
    }

    @GetMapping("/{id}/favorites/{postId}")
    public ResponseEntity<Map<String, Boolean>> isFavorite(@PathVariable Long id, @PathVariable Long postId) {
        requireOwnerOrAdmin(id, "Вы можете просматривать избранное только своего профиля");
        return ResponseEntity.ok(Map.of("isFavorite", userService.isFavorite(id, postId)));
    }

    @GetMapping("/{id}/wallet")
    public ResponseEntity<WalletDto> getWallet(@PathVariable Long id) {
        requireOwnerOrAdmin(id, "Вы можете просматривать только свой кошелёк");
        return ResponseEntity.ok(walletService.getWallet(id));
    }

    @GetMapping("/{id}/wallet/transactions")
    public ResponseEntity<Page<CoinTransactionDto>> getTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionDirection direction,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        requireOwnerOrAdmin(id, "Вы можете просматривать только свои транзакции");
        return ResponseEntity.ok(walletService.getTransactions(id, page, size, type, from, to, direction));
    }

    @GetMapping("/{id}/wallet/pricing")
    public ResponseEntity<WalletPricingDto> getPricing(@PathVariable Long id) {
        requireOwnerOrAdmin(id, "Цены доступны только владельцу аккаунта или администратору");
        return ResponseEntity.ok(walletService.getPricing());
    }

    @PostMapping("/{id}/wallet/daily-bonus")
    public ResponseEntity<Map<String, Object>> claimDailyBonus(@PathVariable Long id) {
        requireOwnerOrAdmin(id, "Вы можете получить бонус только для своего аккаунта");
        boolean claimed = walletService.addDailyLoginBonus(id);
        WalletDto wallet = walletService.getWallet(id);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "claimed", claimed,
            "balance", wallet.getBalance(),
            "message", claimed
                    ? "+" + economyConfig.getCoinsPerDailyLogin() + " HC за ежедневный вход!"
                    : "Ежедневный бонус уже получен"
        ));
    }

    @PostMapping("/{id}/wallet/admin-add")
    public ResponseEntity<Map<String, Object>> adminAddCoins(
            @PathVariable Long id,
            @RequestParam long amount,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String description,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может начислять монеты");
        }
        UserContext admin = authService.getCurrentUser();
        if (admin == null) {
            throw new UnauthorizedException("Вы не авторизованы");
        }
        // description is kept as a legacy alias for reason.
        String resolvedReason = reason != null && !reason.isBlank() ? reason : description;
        WalletOperationResult result = walletService.adminAddCoins(
                id, admin.getUserId(), amount, resolvedReason, idempotencyKey);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "newBalance", result.getBalance(),
            "addedAmount", amount,
            "applied", result.getApplied()
        ));
    }

    /**
     * Server-priced purchase endpoint. The client chooses the operation, never
     * the price or duration; amount/description parameters are intentionally not
     * accepted anymore.
     */
    @PostMapping("/{id}/wallet/spend")
    public ResponseEntity<Map<String, Object>> spendCoins(
            @PathVariable Long id,
            @RequestParam TransactionType type,
            @RequestParam(required = false) String color,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireOwnerOrAdmin(id, "Вы можете тратить монеты только со своего аккаунта");

        WalletOperationResult result;
        if (type == TransactionType.VIP || type == TransactionType.VIP_STATUS) {
            result = walletService.purchaseVip(id, idempotencyKey);
        } else if (type == TransactionType.ACCOUNT_UNBLOCK) {
            result = walletService.purchaseAccountUnblock(id, idempotencyKey);
        } else if (type == TransactionType.NICKNAME_COLOR) {
            if (color == null || !color.matches("^#[0-9A-Fa-f]{6}$")) {
                throw new BadRequestException("Цвет должен быть в формате #RRGGBB (например, #FF5733)");
            }
            result = walletService.changeNicknameColor(id, color, idempotencyKey);
        } else if (type == TransactionType.POST_BOOST) {
            throw new BadRequestException("Для поднятия заявки используйте POST /api/posts/{id}/boost");
        } else {
            throw new BadRequestException("Эта операция недоступна для покупки");
        }

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("success", true);
        body.put("operation", result.getOperation());
        body.put("applied", result.getApplied());
        body.put("spentAmount", result.getPrice());
        body.put("newBalance", result.getBalance());
        if (result.getExpiresAt() != null) {
            body.put("expiresAt", result.getExpiresAt());
        }
        if (result.getNicknameColor() != null) {
            body.put("color", result.getNicknameColor());
        }
        if (result.getDebtCount() != null) {
            body.put("debtCount", result.getDebtCount());
        }
        if (result.getBlocked() != null) {
            body.put("blocked", result.getBlocked());
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/wallet/nickname-color")
    public ResponseEntity<Map<String, Object>> changeNicknameColor(
            @PathVariable Long id,
            @RequestParam String color,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireOwnerOrAdmin(id, "Вы можете менять цвет ника только своего аккаунта");
        if (color == null || !color.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new BadRequestException("Цвет должен быть в формате #RRGGBB (например, #FF5733)");
        }
        WalletOperationResult result = walletService.changeNicknameColor(id, color, idempotencyKey);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "applied", result.getApplied(),
            "newBalance", result.getBalance(),
            "spentAmount", result.getPrice(),
            "color", result.getNicknameColor()
        ));
    }

    private UserContext requireCurrentUser() {
        UserContext user = authService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Вы не авторизованы");
        }
        return user;
    }

    /**
     * Единая проверка владельца/администратора для операций над пользовательскими ресурсами.
     */
    private void requireOwnerOrAdmin(Long targetUserId, String message) {
        if (targetUserId == null || !authService.canManageUser(targetUserId)) {
            throw new ForbiddenException(message);
        }
    }

    /**
     * Directory pagination with a server-side sort whitelist. The id tiebreaker
     * makes deep pages deterministic.
     */
    private Pageable resolveDirectoryPageable(Integer page,
                                              Integer size,
                                              String sort,
                                              String direction,
                                              java.util.Set<String> allowedSorts) {
        String property = sort != null && allowedSorts.contains(sort) ? sort : "id";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort sortSpec = Sort.by(dir, property).and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequests.of(page, size, paginationConfig, sortSpec);
    }
}