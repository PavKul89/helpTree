package org.example.helptreeservice.user;

import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Real-database regression test for the auto-unblock / rating-recalculation
 * race. The bulk UPDATE must clear the persistence context, otherwise the
 * recalculation flushes the stale managed user back with the old block columns.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class UserUnblockIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email LIKE 'unblock-it-%@test.local'", Long.class);
        if (ids.isEmpty()) {
            return;
        }
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM rating_history WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM user_rating_stats WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (" + in + ")");
    }

    @Test
    void unblockExpiredBlocks_shouldKeepBlockColumnsClearedAfterRatingRecalculation() {
        User user = new User();
        user.setName("Unblock IT");
        user.setEmail("unblock-it-" + UUID.randomUUID() + "@test.local");
        user.setPassword("$2a$10$test-only-not-a-real-hash");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setBlockedAt(LocalDateTime.now().minusDays(8));
        user.setBlockedUntil(LocalDateTime.now().minusDays(1));
        user.setDebtCount(0);
        user.setRating(2.0);
        User saved = userRepository.saveAndFlush(user);

        userService.unblockExpiredBlocks();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT blocked_at, blocked_until FROM users WHERE id = ?", saved.getId());
        assertNull(row.get("blocked_at"), "blocked_at must stay NULL after rating recalculation");
        assertNull(row.get("blocked_until"), "blocked_until must stay NULL after rating recalculation");

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertEquals(3.0, reloaded.getRating(),
                "The block penalty must be removed by the recalculation in the same transaction");
    }
}
