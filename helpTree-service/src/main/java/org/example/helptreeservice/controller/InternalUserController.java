package org.example.helptreeservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final UserService userService;

    @PutMapping("/{id}/rating")
    public ResponseEntity<Void> updateUserRating(
            @PathVariable Long id,
            @RequestParam Double rating) {
        log.info("Внутреннее обновление рейтинга пользователя ID: {} на {}", id, rating);
        userService.updateUserRating(id, rating);
        return ResponseEntity.ok().build();
    }
}
