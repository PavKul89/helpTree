package org.example.helptreeservice.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.PasswordService;
import org.example.helptreeservice.util.SensitiveDataMasker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Creates the first administrator exactly once, and only when explicitly
 * requested.
 *
 * <p>Safety properties:</p>
 * <ul>
 *   <li>the whole decision and insert run in one transaction;</li>
 *   <li>a PostgreSQL transaction-scoped advisory lock serializes concurrent
 *       instances, so two replicas starting together cannot create two
 *       administrators;</li>
 *   <li>an existing active administrator or an already-used email aborts the
 *       bootstrap instead of overwriting an account;</li>
 *   <li>the password must pass {@link SecretRules#isStrongPassword(String)};</li>
 *   <li>the email is masked in logs.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBootstrapService {

    /**
     * Arbitrary but fixed key for {@code pg_advisory_xact_lock}. The lock is
     * released automatically when the transaction commits or rolls back.
     */
    private static final long BOOTSTRAP_LOCK_KEY = 0x68656C70L;

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final EntityManager entityManager;

    /**
     * @return {@code true} when this call created the administrator
     */
    @Transactional
    public boolean bootstrapIfEnabled() {
        if (!properties.isBootstrapEnabled()) {
            return false;
        }

        String email = normalizeEmail(properties.getEmail());
        String password = properties.getPassword();
        if (!SecretRules.isEmail(email)) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL must be a valid address when ADMIN_BOOTSTRAP_ENABLED=true");
        }
        if (!SecretRules.isStrongPassword(password)) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be a strong password when ADMIN_BOOTSTRAP_ENABLED=true");
        }

        // Serialize the check-and-create across application instances.
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                .setParameter("key", BOOTSTRAP_LOCK_KEY)
                .getSingleResult();

        if (userRepository.countActiveByRole(Role.ADMIN) > 0) {
            log.info("Administrator bootstrap skipped: an active administrator already exists");
            return false;
        }

        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL is already used by an existing account ("
                            + SensitiveDataMasker.maskEmail(email)
                            + "); choose another address or clear ADMIN_BOOTSTRAP_ENABLED");
        }

        LocalDateTime now = LocalDateTime.now();
        User admin = new User();
        admin.setEmail(email);
        admin.setPassword(passwordService.encode(password));
        admin.setName("Admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setEmailVerified(true);
        admin.setTokenVersion(0L);
        admin.setHelpedCount(0);
        admin.setDebtCount(0);
        admin.setRating(3.0);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);

        userRepository.save(admin);
        log.info("Bootstrap administrator created: id={} email={}",
                admin.getId(), SensitiveDataMasker.maskEmail(email));
        return true;
    }

    private String normalizeEmail(String rawEmail) {
        return rawEmail == null ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
    }
}
