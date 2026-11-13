package org.example.helptreeservice.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
    @SchedulerLock(name = "DebtBlockingScheduler_checkAndBlockUsersWithDebt",
            lockAtMostFor = "PT55M", lockAtLeastFor = "PT1M")
    public void checkAndBlockUsersWithDebt() {
        log.info("Запуск планировщика проверки блокировки пользователей за долг");
        try {
            userService.unblockExpiredBlocks();
            userService.blockUsersWithDebt();
        } catch (Exception e) {
            log.error("Ошибка при блокировке пользователей за долг: {}", e.getMessage(), e);
        }
    }
}
