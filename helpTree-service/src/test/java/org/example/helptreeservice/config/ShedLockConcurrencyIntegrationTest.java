package org.example.helptreeservice.config;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ShedLockConcurrencyIntegrationTest.LockingTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ShedLockConcurrencyIntegrationTest {

    @Container
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("shedlock_test")
            .withUsername("shedlock")
            .withPassword("shedlock");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LockTestTask lockTestTask;

    @BeforeEach
    void createLockTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS shedlock (
                    name VARCHAR(64) NOT NULL PRIMARY KEY,
                    lock_until TIMESTAMP NOT NULL,
                    locked_at TIMESTAMP NOT NULL,
                    locked_by VARCHAR(255) NOT NULL
                )
                """);
        lockTestTask.reset();
    }

    @Test
    void concurrentCallsRunTheLockedMethodOnlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstCall = executor.submit(lockTestTask::run);
            assertTrue(lockTestTask.awaitStarted(5, TimeUnit.SECONDS),
                    "The first invocation should acquire the database lock");

            Future<?> secondCall = executor.submit(lockTestTask::run);
            secondCall.get(5, TimeUnit.SECONDS);

            assertEquals(1, lockTestTask.executionCount(),
                    "The second invocation must be skipped while the first holds the lock");

            lockTestTask.release();
            firstCall.get(5, TimeUnit.SECONDS);
            assertEquals(1, lockTestTask.executionCount());
        } finally {
            lockTestTask.release();
            executor.shutdownNow();
        }
    }

    @Configuration
    @Import(ShedLockConfig.class)
    static class LockingTestConfiguration {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        LockTestTask lockTestTask() {
            return new LockTestTask();
        }
    }

    public static class LockTestTask {
        private final AtomicInteger executions = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @SchedulerLock(name = "ShedLockConcurrencyIntegrationTest_run",
                lockAtMostFor = "PT10M", lockAtLeastFor = "PT0S")
        public void run() {
            executions.incrementAndGet();
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test task interrupted", exception);
            }
        }

        void reset() {
            executions.set(0);
        }

        int executionCount() {
            return executions.get();
        }

        boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
            return started.await(timeout, unit);
        }

        void release() {
            release.countDown();
        }
    }
}
