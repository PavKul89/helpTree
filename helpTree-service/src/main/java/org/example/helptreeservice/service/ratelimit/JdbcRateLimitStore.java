package org.example.helptreeservice.service.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL-backed store. Every mutating call runs in its own transaction
 * ({@code REQUIRES_NEW}): a request that later fails must not roll back the
 * request that was already counted, otherwise an attacker could reset the
 * limiter by sending deliberately invalid requests.
 *
 * The increment is a single {@code INSERT ... ON CONFLICT} statement, so two
 * instances racing on the same key are serialized by PostgreSQL and see one
 * shared counter.
 */
@Component
@RequiredArgsConstructor
public class JdbcRateLimitStore implements RateLimitStore {

    private static final String INCREMENT_SQL = """
            INSERT INTO rate_limit_buckets AS bucket
                (bucket_key, window_started_at, request_count, expires_at)
            VALUES
                (?, LOCALTIMESTAMP, 1, LOCALTIMESTAMP + (? * INTERVAL '1 second'))
            ON CONFLICT (bucket_key) DO UPDATE SET
                request_count = CASE
                    WHEN bucket.expires_at <= LOCALTIMESTAMP THEN 1
                    ELSE bucket.request_count + 1
                END,
                window_started_at = CASE
                    WHEN bucket.expires_at <= LOCALTIMESTAMP THEN EXCLUDED.window_started_at
                    ELSE bucket.window_started_at
                END,
                expires_at = CASE
                    WHEN bucket.expires_at <= LOCALTIMESTAMP THEN EXCLUDED.expires_at
                    ELSE bucket.expires_at
                END
            RETURNING request_count,
                      EXTRACT(EPOCH FROM (expires_at - LOCALTIMESTAMP)) AS retry_after
            """;

    private static final String FIND_LOCK_SQL = """
            SELECT CEIL(EXTRACT(EPOCH FROM (locked_until - LOCALTIMESTAMP)))::bigint
            FROM login_locks
            WHERE lock_key = ?
              AND locked_until > LOCALTIMESTAMP
            """;

    private static final String UPSERT_LOCK_SQL = """
            INSERT INTO login_locks (lock_key, locked_until, created_at)
            VALUES (?, LOCALTIMESTAMP + (? * INTERVAL '1 second'), LOCALTIMESTAMP)
            ON CONFLICT (lock_key) DO UPDATE SET
                locked_until = GREATEST(login_locks.locked_until, EXCLUDED.locked_until)
            """;

    private static final String DELETE_EXPIRED_BUCKETS_SQL = """
            DELETE FROM rate_limit_buckets
            WHERE bucket_key IN (
                SELECT bucket_key
                FROM rate_limit_buckets
                WHERE expires_at <= LOCALTIMESTAMP
                LIMIT ?
            )
            """;

    private static final String DELETE_EXPIRED_LOCKS_SQL = """
            DELETE FROM login_locks
            WHERE lock_key IN (
                SELECT lock_key
                FROM login_locks
                WHERE locked_until <= LOCALTIMESTAMP
                LIMIT ?
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RateLimitDecision increment(String key, int limit, long windowSeconds) {
        int safeLimit = Math.max(1, limit);
        long safeWindow = Math.max(1, windowSeconds);

        return jdbcTemplate.query(INCREMENT_SQL, rs -> {
            if (!rs.next()) {
                throw new IllegalStateException("Rate-limit counter upsert returned no row");
            }
            int count = rs.getInt("request_count");
            long retryAfter = Math.max(1, Math.round(rs.getDouble("retry_after")));
            if (count > safeLimit) {
                return RateLimitDecision.rejected(safeLimit, retryAfter);
            }
            return RateLimitDecision.allowed(safeLimit, safeLimit - count, retryAfter);
        }, key, safeWindow);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(String key) {
        jdbcTemplate.update("DELETE FROM rate_limit_buckets WHERE bucket_key = ?", key);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteLock(String key) {
        jdbcTemplate.update("DELETE FROM login_locks WHERE lock_key = ?", key);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Long> findActiveLock(String key) {
        List<Long> rows = jdbcTemplate.query(FIND_LOCK_SQL,
                (rs, rowNum) -> Math.max(1, rs.getLong(1)), key);
        return rows.stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acquireLock(String key, long durationSeconds) {
        jdbcTemplate.update(UPSERT_LOCK_SQL, key, Math.max(1, durationSeconds));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteExpiredBuckets(int batchSize) {
        return jdbcTemplate.update(DELETE_EXPIRED_BUCKETS_SQL, Math.max(1, batchSize));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteExpiredLocks(int batchSize) {
        return jdbcTemplate.update(DELETE_EXPIRED_LOCKS_SQL, Math.max(1, batchSize));
    }
}
