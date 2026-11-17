package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal", description = "Внутренние сервисные endpoints")
public class InternalUserController {

    private final UserService userService;
    private final AuthorizationService authService;

    @PutMapping("/{id}/rating")
    @Operation(summary = "Внутреннее обновление рейтинга",
            description = "Служебный endpoint; доступен только администраторам.")
    public ResponseEntity<Void> updateUserRating(
            @PathVariable Long id,
            @RequestParam Double rating) {
        // The JWT filter already rejects non-admin traffic to /internal/**, but
        // the controller must not rely on a filter for its authorization rule.
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может изменять рейтинг");
        }
        log.info("Внутреннее обновление рейтинга пользователя ID: {} на {}", id, rating);
        userService.updateUserRating(id, rating);
        return ResponseEntity.ok().build();
    }
}
