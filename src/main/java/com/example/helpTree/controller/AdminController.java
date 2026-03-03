package com.example.helpTree.controller;

import com.example.helpTree.dto.users.UserDto;
import com.example.helpTree.enums.Role;
import com.example.helpTree.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserDto> changeUserRole(@PathVariable Long id, @RequestParam Role role) {
        // Здесь нужно добавить метод в сервис для смены роли
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{id}/hard")
    public ResponseEntity<Void> hardDeleteUser(@PathVariable Long id) {
        // Полное удаление из БД (только для админов)
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{id}/restore")
    public ResponseEntity<Void> restoreUser(@PathVariable Long id) {
        userService.restoreUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getSystemStats() {
        // Статистика системы
        return ResponseEntity.ok().build();
    }
}
