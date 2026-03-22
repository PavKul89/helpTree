package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.entity.RefreshToken;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.repository.RefreshTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    
    private int refreshTokenDays = 7;

    @Transactional
    public String createRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenDays))
                .revoked(false)
                .build();
        
        refreshTokenRepository.save(refreshToken);
        
        log.debug("Создан refresh токен для пользователя: {}", user.getId());
        
        return token;
    }

    @Transactional(readOnly = true)
    public RefreshToken validateToken(String token) {
        return refreshTokenRepository.findValidToken(token, LocalDateTime.now())
                .orElse(null);
    }

    @Transactional
    public void revokeToken(String token, Long userId) {
        refreshTokenRepository.deleteByUserIdAndToken(userId, token);
        log.info("Refresh токен отозван: userId={}", userId);
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Все refresh токены пользователя {} отозваны", userId);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.debug("Удалены просроченные refresh токены");
    }

    public void setRefreshTokenDays(int days) {
        this.refreshTokenDays = days;
    }

    public int getRefreshTokenDays() {
        return refreshTokenDays;
    }
}
