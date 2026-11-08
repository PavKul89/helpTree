package org.example.helptreeservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Startup hook for the opt-in {@link AdminBootstrapService}.
 *
 * <p>It never creates an account on its own: the service must be enabled
 * explicitly and validates both the address and the password. When no active
 * administrator exists and the bootstrap is off, the application still starts
 * and emits a warning instead of silently using a well-known default.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final AdminBootstrapService adminBootstrapService;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        boolean created = adminBootstrapService.bootstrapIfEnabled();
        if (!created && userRepository.countActiveByRole(Role.ADMIN) == 0) {
            log.warn("No active administrator exists. Set ADMIN_BOOTSTRAP_ENABLED=true with "
                    + "ADMIN_EMAIL and a strong ADMIN_PASSWORD for the first start, then "
                    + "disable the bootstrap.");
        }
    }
}
