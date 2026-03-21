package org.example.helptreeservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UpdateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.dto.users.UserPublicDto;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.AuthorizationService.UserContext;
import org.example.helptreeservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthorizationService authService;

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
}