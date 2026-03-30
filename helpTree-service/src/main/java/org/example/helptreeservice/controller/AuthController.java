package org.example.helptreeservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.auth.AuthResponse;
import java.time.LocalDateTime;
import org.example.helptreeservice.dto.auth.LoginRequest;
import org.example.helptreeservice.dto.auth.RefreshTokenRequest;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.entity.RefreshToken;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.JwtService;
import org.example.helptreeservice.service.PasswordService;
import org.example.helptreeservice.service.RefreshTokenService;
import org.example.helptreeservice.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody CreateUserRequest request) {
        UserDto user = userService.createUser(request);
        
        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
        
        String refreshToken = refreshTokenService.createRefreshToken(
                userService.getUserEntityById(user.getId())
        );

        long expiresIn = jwtService.getExpirationTime();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(accessToken, refreshToken, user.getId(), user.getEmail(), user.getRole().name(), expiresIn));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.getUserEntityByEmail(request.getEmail());
        
        if (!passwordService.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Неверный email или пароль");
        }

        if (user.getDeleted()) {
            throw new UnauthorizedException("Аккаунт удалён");
        }

        LocalDateTime previousLastLogin = user.getLastLogin();
        userService.updateLastLogin(user.getId());

        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
        
        String refreshToken = refreshTokenService.createRefreshToken(user);

        long expiresIn = jwtService.getExpirationTime();

        AuthResponse response = new AuthResponse(accessToken, refreshToken, user.getId(), user.getEmail(), user.getRole().name(), expiresIn);
        response.setPreviousLastLogin(previousLastLogin);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        Long userId;
        try {
            userId = Long.parseLong(request.getUserId());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Неверный формат userId");
        }

        RefreshToken refreshToken = refreshTokenService.validateToken(request.getRefreshToken());
        
        if (refreshToken == null || !refreshToken.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Невалидный или истёкший refresh токен");
        }

        User user = refreshToken.getUser();
        
        refreshTokenService.revokeToken(request.getRefreshToken(), userId);
        
        String newAccessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
        
        String newRefreshToken = refreshTokenService.createRefreshToken(user);

        long expiresIn = jwtService.getExpirationTime();

        log.info("Токены обновлены для пользователя: {}", userId);

        return ResponseEntity.ok(new AuthResponse(newAccessToken, newRefreshToken, user.getId(), user.getEmail(), user.getRole().name(), expiresIn));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        Long userId;
        try {
            userId = Long.parseLong(request.getUserId());
        } catch (NumberFormatException e) {
            return ResponseEntity.ok().build();
        }

        refreshTokenService.revokeAllUserTokens(userId);
        
        log.info("Пользователь вышел из системы: {}", userId);
        
        return ResponseEntity.ok().build();
    }
}
