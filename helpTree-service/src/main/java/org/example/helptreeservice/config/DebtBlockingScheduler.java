package org.example.helptreeservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.service.UserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DebtBlockingScheduler {

    private final UserService userService;

    @Scheduled(cron = "0 0 * * * *")
    public void checkAndBlockUsersWithDebt() {
        log.info("Запуск планировщика проверки блокировки пользователей за долг");
        try {
            userService.blockUsersWithDebt();
        } catch (Exception e) {
            log.error("Ошибка при блокировке пользователей за долг: {}", e.getMessage(), e);
        }
    }
}
