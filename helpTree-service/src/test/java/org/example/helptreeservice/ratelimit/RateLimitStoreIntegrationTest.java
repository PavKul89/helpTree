package org.example.helptreeservice.ratelimit;

import org.example.helptreeservice.service.ratelimit.JdbcRateLimitStore;
import org.example.helptreeservice.service.ratelimit.RateLimitDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that two independent store objects (the equivalent of two application
 * instances) share one atomic counter in PostgreSQL. Runs only with the real
 * integration database because the whole point is the SQL upsert.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class RateLimitStoreIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;
    private JdbcRateLimitStore firstInstance;
    private JdbcRateLimitStore secondInstance;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        firstInstance = new JdbcRateLimitStore(jdbcTemplate);
        secondInstance = new JdbcRateLimitStore(jdbcTemplate);
    }

    @Test
    void concurrentIncrementsFromTwoInstancesShareOneCounter() throws Exception {
        String key = "test:rate:" + UUID.randomUUID();
        int attempts = 60;
        int limit = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(8);

        try {
            List<Future<RateLimitDecision>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                JdbcRateLimitStore instance = i % 2 == 0 ? firstInstance : secondInstance;
                futures.add(pool.submit(() -> instance.increment(key, limit, 60)));
            }

            Set<Integer> observedCounts = new HashSet<>();
            for (Future<RateLimitDecision> future : futures) {
                RateLimitDecision decision = future.get(10, TimeUnit.SECONDS);
                assertTrue(decision.allowed());
                observedCounts.add(limit - decision.remaining());
            }

            assertEquals(attempts, observedCounts.size(),
                    "Every concurrent increment must observe a distinct counter value");
            assertEquals(attempts, jdbcTemplate.queryForObject(
                    "SELECT request_count FROM rate_limit_buckets WHERE bucket_key = ?",
                    Integer.class, key));
        } finally {
            pool.shutdownNow();
            firstInstance.delete(key);
        }
    }

    @Test
    void counterRejectsRequestsAboveTheLimit() {
        String key = "test:rate:" + UUID.randomUUID();
        try {
            for (int i = 0; i < 3; i++) {
                assertTrue(firstInstance.increment(key, 3, 60).allowed());
            }

            RateLimitDecision rejected = secondInstance.increment(key, 3, 60);

            assertFalse(rejected.allowed());
            assertEquals(0, rejected.remaining());
            assertTrue(rejected.retryAfterSeconds() >= 1);
        } finally {
            firstInstance.delete(key);
        }
    }

    @Test
    void lockIsVisibleAcrossInstancesAndExpiresAutomatically() {
        String key = "test:lock:" + UUID.randomUUID();
        try {
            firstInstance.acquireLock(key, 60);

            assertTrue(secondInstance.findActiveLock(key).orElse(0L) > 0);

            jdbcTemplate.update(
                    "UPDATE login_locks SET locked_until = LOCALTIMESTAMP - INTERVAL '1 second' "
                            + "WHERE lock_key = ?", key);

            assertTrue(secondInstance.findActiveLock(key).isEmpty());
        } finally {
            firstInstance.deleteLock(key);
        }
    }
}
