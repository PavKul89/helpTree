package org.example.helptreeservice.service.ratelimit;

import org.example.helptreeservice.config.LoginProtectionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginProtectionServiceTest {

    private RateLimitStore store;
    private LoginProtectionProperties properties;
    private LoginProtectionService service;

    @BeforeEach
    void setUp() {
        store = mock(RateLimitStore.class);
        properties = new LoginProtectionProperties();
        properties.setEnabled(true);
        properties.setMaxAttempts(5);
        properties.setFailureWindowMinutes(15);
        properties.setLockoutMinutes(15);
        service = new LoginProtectionService(store, properties);
    }

    @Test
    void retryAfterSeconds_shouldReturnActiveLock() {
        when(store.findActiveLock(anyString())).thenReturn(Optional.of(321L));

        assertEquals(Optional.of(321L), service.retryAfterSeconds("203.0.113.7", "user@test.com"));
    }

    @Test
    void retryAfterSeconds_shouldFailOpenWhenStoreIsUnavailable() {
        when(store.findActiveLock(anyString())).thenThrow(new IllegalStateException("db down"));

        assertTrue(service.retryAfterSeconds("203.0.113.7", "user@test.com").isEmpty());
    }

    @Test
    void recordFailure_belowThresholdShouldNotLock() {
        when(store.increment(anyString(), eq(5), eq(900L)))
                .thenReturn(RateLimitDecision.allowed(5, 1, 900));

        assertTrue(service.recordFailure("203.0.113.7", "user@test.com").isEmpty());
        verify(store, never()).acquireLock(anyString(), anyLong());
    }

    @Test
    void recordFailure_atThresholdShouldCreateLockAndResetCounter() {
        when(store.increment(anyString(), eq(5), eq(900L)))
                .thenReturn(RateLimitDecision.allowed(5, 0, 900));

        Optional<Long> lockout = service.recordFailure("203.0.113.7", "user@test.com");

        assertEquals(Optional.of(900L), lockout);
        verify(store).acquireLock(anyString(), eq(900L));
        verify(store, times(1)).delete(anyString());
    }

    @Test
    void recordFailure_shouldKeyByIpAndAccountSoAnotherIpCannotLockTheAccount() {
        when(store.increment(anyString(), anyInt(), anyLong()))
                .thenReturn(RateLimitDecision.allowed(5, 4, 900));

        service.recordFailure("198.51.100.1", "victim@test.com");
        service.recordFailure("198.51.100.2", "victim@test.com");

        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).increment(keys.capture(), anyInt(), anyLong());
        assertEquals(2, keys.getAllValues().stream().distinct().count());
    }

    @Test
    void recordSuccess_shouldClearCounterAndLock() {
        service.recordSuccess("203.0.113.7", "user@test.com");

        verify(store).delete(anyString());
        verify(store).deleteLock(anyString());
    }

    @Test
    void disabledProtectionShouldBeNoOp() {
        properties.setEnabled(false);

        assertTrue(service.retryAfterSeconds("203.0.113.7", "user@test.com").isEmpty());
        assertTrue(service.recordFailure("203.0.113.7", "user@test.com").isEmpty());
        service.recordSuccess("203.0.113.7", "user@test.com");

        verify(store, never()).increment(anyString(), anyInt(), anyLong());
    }
}
