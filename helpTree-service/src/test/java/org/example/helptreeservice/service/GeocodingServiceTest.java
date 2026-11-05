package org.example.helptreeservice.service;

import org.example.helptreeservice.config.GeocodingConfig;
import org.example.helptreeservice.entity.GeocodingCacheEntry;
import org.example.helptreeservice.repository.GeocodingCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The request path must stay network-free: only a resolved cache entry is read,
 * unresolved cities are queued, and the worker is the only code that calls
 * Nominatim.
 */
@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private GeocodingCacheRepository cacheRepository;

    private GeocodingConfig config;
    private GeocodingService service;

    @BeforeEach
    void setUp() {
        config = new GeocodingConfig();
        config.setEnabled(true);
        config.setBatchSize(1);
        config.setMaxAttempts(5);
        service = new GeocodingService(restTemplate, cacheRepository, config);
    }

    @Test
    void cached_returnsCoordinatesOnlyForResolvedCity() {
        GeocodingCacheEntry found = entry("minsk", GeocodingCacheEntry.FOUND, 53.9, 27.56);
        when(cacheRepository.findById("minsk")).thenReturn(Optional.of(found));

        Optional<GeocodingService.GeoLocation> location = service.cached("  Minsk ");

        assertTrue(location.isPresent());
        assertEquals(53.9, location.get().lat());
        assertEquals(27.56, location.get().lng());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void cached_ignoresPendingAndFailedCities() {
        when(cacheRepository.findById("minsk"))
                .thenReturn(Optional.of(entry("minsk", GeocodingCacheEntry.PENDING, null, null)));

        assertTrue(service.cached("Minsk").isEmpty());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void enqueue_normalizesCityAndIsIdempotent() {
        when(cacheRepository.enqueue(eq("minsk"), anyString(), any(LocalDateTime.class)))
                .thenReturn(1, 0);

        assertTrue(service.enqueue("Minsk"));
        assertFalse(service.enqueue("  MINSK  "));
        verify(cacheRepository, org.mockito.Mockito.times(2))
                .enqueue(eq("minsk"), anyString(), any(LocalDateTime.class));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void enqueue_skipsBlankCity() {
        assertFalse(service.enqueue("   "));
        verifyNoInteractions(cacheRepository, restTemplate);
    }

    @Test
    void disabledWorker_neverTouchesQueueOrNetwork() {
        config.setEnabled(false);

        service.processPendingBatch();

        verifyNoInteractions(cacheRepository, restTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void workerResolvesPendingCityAndBackfillsRows() {
        GeocodingCacheEntry pending = entry("minsk", GeocodingCacheEntry.PENDING, null, null);
        pending.setCreatedAt(LocalDateTime.now());
        when(cacheRepository.findPendingForUpdate(anyInt(), any(LocalDateTime.class),
                any(PageRequest.class))).thenReturn(List.of(pending));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(List.class))).thenReturn(new ResponseEntity<>(
                List.of(Map.of("lat", "53.9", "lon", "27.56")), HttpStatus.OK));
        when(cacheRepository.backfillUsers(any(), anyDouble(), anyDouble(), any())).thenReturn(3);
        when(cacheRepository.backfillPosts(any(), anyDouble(), anyDouble(), any())).thenReturn(4);

        service.processPendingBatch();

        assertEquals(GeocodingCacheEntry.FOUND, pending.getStatus());
        assertEquals(53.9, pending.getLatitude(), 1e-9);
        assertEquals(27.56, pending.getLongitude(), 1e-9);
        assertEquals(1, pending.getAttempts());
        assertTrue(pending.getResolvedAt() != null);
        verify(cacheRepository).backfillUsers(any(), anyDouble(), anyDouble(), any());
        verify(cacheRepository).backfillPosts(any(), anyDouble(), anyDouble(), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void workerKeepsRetryingUntilMaxAttempts() {
        config.setMaxAttempts(3);
        GeocodingCacheEntry pending = entry("minsk", GeocodingCacheEntry.PENDING, null, null);
        pending.setAttempts(1);
        pending.setCreatedAt(LocalDateTime.now());
        when(cacheRepository.findPendingForUpdate(anyInt(), any(LocalDateTime.class),
                any(PageRequest.class))).thenReturn(List.of(pending));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(List.class))).thenReturn(new ResponseEntity<>(List.of(), HttpStatus.OK));

        service.processPendingBatch();

        assertEquals(GeocodingCacheEntry.PENDING, pending.getStatus());
        assertEquals(2, pending.getAttempts());
        verify(cacheRepository, never()).backfillUsers(any(), anyDouble(), anyDouble(), any());
    }

    private GeocodingCacheEntry entry(String key, String status, Double lat, Double lng) {
        GeocodingCacheEntry entry = new GeocodingCacheEntry();
        entry.setCityKey(key);
        entry.setQueryCity(key);
        entry.setStatus(status);
        entry.setLatitude(lat);
        entry.setLongitude(lng);
        entry.setAttempts(0);
        entry.setUpdatedAt(LocalDateTime.now());
        return entry;
    }
}
