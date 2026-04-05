package org.example.helptreeservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UpdateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.dto.users.UserPublicDto;
import org.example.helptreeservice.dto.wallet.CoinTransactionDto;
import org.example.helptreeservice.dto.wallet.WalletDto;
import org.example.helptreeservice.enums.TransactionType;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.AuthorizationService.UserContext;
import org.example.helptreeservice.service.UserService;
import org.example.helptreeservice.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может создавать пользователей");
        }
        UserDto created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может просматривать всех пользователей");
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/public")
    public ResponseEntity<List<UserPublicDto>> getAllUsersPublic() {
        return ResponseEntity.ok(userService.getAllUsersPublic());
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        UserContext user = authService.getCurrentUser();
        if (user == null) {
            throw new ForbiddenException("Вы не авторизованы");
        }
        return ResponseEntity.ok(userService.getUserById(user.getUserId()));
    }

    @GetMapping("/{id}/public")
    public ResponseEntity<UserPublicDto> getUserPublic(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserPublicById(id));
    }

    @PostMapping("/public/batch")
    public ResponseEntity<List<UserPublicDto>> getUsersPublicBatch(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(userService.getUsersPublicByIds(ids));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете просматривать только свой профиль");
        }
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
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете редактировать только свой профиль");
        }
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете удалить только свой аккаунт");
        }
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

    @PostMapping("/{id}/increment-help")
    public ResponseEntity<Void> incrementHelpedCount(@PathVariable Long id) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Эта операция доступна только администратору");
        }
        userService.incrementHelpedCount(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/help/{helperId}/to/{receiverId}")
    public ResponseEntity<String> helpUser(
            @PathVariable Long helperId,
            @PathVariable Long receiverId) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Эта операция доступна только администратору");
        }
        userService.processHelp(helperId, receiverId);
        return ResponseEntity.ok("Помощь зафиксирована");
    }

    @PostMapping("/increment-debt/{receiverId}")
    public ResponseEntity<String> incrementDebt(@PathVariable Long receiverId) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Эта операция доступна только администратору");
        }
        userService.incrementHelpedCount(receiverId);
        return ResponseEntity.ok("Долг увеличен");
    }

    @PostMapping("/helped/{helperId}")
    public ResponseEntity<String> helped(@PathVariable Long helperId) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Эта операция доступна только администратору");
        }
        userService.userHelpedSomeone(helperId);
        return ResponseEntity.ok("Помощь оказана");
    }

    @GetMapping("/{id}/telegram-chat-id")
    public ResponseEntity<String> getTelegramChatId(@PathVariable Long id) {
        String chatId = userService.getTelegramChatId(id);
        return ResponseEntity.ok(chatId != null ? chatId : "");
    }

    @PutMapping("/{id}/telegram")
    public ResponseEntity<Void> bindTelegram(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете привязать Telegram только к своему аккаунту");
        }
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
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете загрузить аватар только для своего профиля");
        }
        String avatarUrl = userService.uploadAvatar(id, file);
        return ResponseEntity.ok(Map.of("url", avatarUrl));
    }

    @PostMapping("/{id}/favorites/{postId}")
    public ResponseEntity<Void> addFavorite(@PathVariable Long id, @PathVariable Long postId) {
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете добавлять в избранное только для своего профиля");
        }
        userService.addFavorite(id, postId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/favorites/{postId}")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long id, @PathVariable Long postId) {
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете удалять из избранного только для своего профиля");
        }
        userService.removeFavorite(id, postId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/favorites")
    public ResponseEntity<List<Long>> getFavorites(@PathVariable Long id) {
        try {
            List<Long> favorites = userService.getFavorites(id);
            return ResponseEntity.ok(favorites != null ? favorites : java.util.Collections.emptyList());
        } catch (Exception e) {
            log.error("Error getting favorites for user {}: {}", id, e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
    }

    @GetMapping("/{id}/favorites/{postId}")
    public ResponseEntity<Map<String, Boolean>> isFavorite(@PathVariable Long id, @PathVariable Long postId) {
        return ResponseEntity.ok(Map.of("isFavorite", userService.isFavorite(id, postId)));
    }

    @GetMapping("/{id}/wallet")
    public ResponseEntity<WalletDto> getWallet(@PathVariable Long id) {
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете просматривать только свой кошелёк");
        }
        return ResponseEntity.ok(walletService.getWallet(id));
    }

    @GetMapping("/{id}/wallet/transactions")
    public ResponseEntity<Page<CoinTransactionDto>> getTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете просматривать только свои транзакции");
        }
        return ResponseEntity.ok(walletService.getTransactions(id, page, size));
    }

    @PostMapping("/{id}/wallet/daily-bonus")
    public ResponseEntity<Map<String, Object>> claimDailyBonus(@PathVariable Long id) {
        if (authService.getCurrentUser() == null) {
            throw new ForbiddenException("Необходимо авторизоваться");
        }
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете получить бонус только для своего аккаунта");
        }
        walletService.addDailyLoginBonus(id);
        WalletDto wallet = walletService.getWallet(id);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "balance", wallet.getBalance(),
            "message", "+1 HC за ежедневный вход!"
        ));
    }

    @PostMapping("/{id}/wallet/admin-add")
    public ResponseEntity<Map<String, Object>> adminAddCoins(
            @PathVariable Long id,
            @RequestParam long amount,
            @RequestParam(required = false) String description) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может начислять монеты");
        }
        walletService.adminAddCoins(id, amount, description);
        WalletDto wallet = walletService.getWallet(id);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "newBalance", wallet.getBalance(),
            "addedAmount", amount
        ));
    }

    @PostMapping("/{id}/wallet/spend")
    public ResponseEntity<Map<String, Object>> spendCoins(
            @PathVariable Long id,
            @RequestParam long amount,
            @RequestParam TransactionType type,
            @RequestParam String description) {
        if (!authService.canManageUser(id)) {
            throw new ForbiddenException("Вы можете тратить монеты только со своего аккаунта");
        }
        walletService.spendCoins(id, amount, type, description);
        WalletDto wallet = walletService.getWallet(id);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "newBalance", wallet.getBalance(),
            "spentAmount", amount
        ));
    }
}