package org.example.helptreeservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UpdateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserDto> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PutMapping("/{id}/rating")
    public ResponseEntity<Void> updateUserRating(
            @PathVariable Long id,
            @RequestParam Double rating) {
        log.info("Обновление рейтинга пользователя ID: {}, новый рейтинг: {}", id, rating);
        userService.updateUserRating(id, rating);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restoreUser(@PathVariable Long id) {
        userService.restoreUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/increment-help")
    public ResponseEntity<Void> incrementHelpedCount(@PathVariable Long id) {
        userService.incrementHelpedCount(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/help/{helperId}/to/{receiverId}")
    public ResponseEntity<String> helpUser(
            @PathVariable Long helperId,
            @PathVariable Long receiverId) {
        userService.processHelp(helperId, receiverId);
        return ResponseEntity.ok("Помощь зафиксирована");
    }

    @PostMapping("/increment-debt/{receiverId}")
    public ResponseEntity<String> incrementDebt(@PathVariable Long receiverId) {
        userService.incrementHelpedCount(receiverId);
        return ResponseEntity.ok("Долг увеличен");
    }

    @PostMapping("/helped/{helperId}")
    public ResponseEntity<String> helped(@PathVariable Long helperId) {
        userService.userHelpedSomeone(helperId);
        return ResponseEntity.ok("Помощь оказана");
    }
}