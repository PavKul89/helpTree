package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.GeocodingConfig;
import org.example.helptreeservice.entity.GeocodingCacheEntry;
import org.example.helptreeservice.repository.GeocodingCacheRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Durable geocoding cache + asynchronous Nominatim worker.
 *
 * <p>Requests never call Nominatim. They only read a resolved city from the
 * cache and enqueue an unresolved one. The scheduled worker processes one row
 * per configured interval, which respects the Nominatim usage policy and keeps
 * the user request path fast and side-effect free.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeocodingService {

    private final RestTemplate restTemplate;
    private final GeocodingCacheRepository cacheRepository;
    private final GeocodingConfig config;

    /**
     * Returns coordinates only when the city is already resolved. Never blocks
     * on the network.
     */
    @Transactional(readOnly = true)
    public Optional<GeoLocation> cached(String cityName) {
        String key = normalizeKey(cityName);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return cacheRepository.findById(key)
                .filter(entry -> GeocodingCacheEntry.FOUND.equals(entry.getStatus()))
                .filter(entry -> entry.getLatitude() != null && entry.getLongitude() != null)
                .map(entry -> new GeoLocation(entry.getLatitude(), entry.getLongitude()));
    }

    /**
     * Idempotently adds a city to the durable queue. A resolved city is left
     * untouched; a terminal failed city is not retried forever.
     */
    @Transactional
    public boolean enqueue(String cityName) {
        String queryCity = cityName != null ? cityName.trim() : "";
        String key = normalizeKey(queryCity);
        if (key.isEmpty()) {
            return false;
        }
        int inserted = cacheRepository.enqueue(key, truncate(queryCity, 255), LocalDateTime.now());
        if (inserted > 0) {
            log.debug("Геокодирование поставлено в очередь: '{}' ({})", queryCity, key);
        }
        return inserted > 0;
    }

    /**
     * Processes at most {@code app.geocoding.batch-size} pending cities. The
     * row lock and the PROCESSING status make the worker safe against duplicate
     * runs; a stale PROCESSING row is reclaimed after
     * {@code app.geocoding.stale-minutes}.
     */
    @Transactional
    public void processPendingBatch() {
        if (!config.isEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusMinutes(config.getStaleMinutes());
        int batchSize = Math.max(1, Math.min(config.getBatchSize(), 25));

        List<GeocodingCacheEntry> pending = cacheRepository.findPendingForUpdate(
                Math.max(1, config.getMaxAttempts()),
                staleBefore,
                PageRequest.of(0, batchSize));

        for (GeocodingCacheEntry entry : pending) {
            entry.setStatus(GeocodingCacheEntry.PROCESSING);
            entry.setUpdatedAt(now);
            cacheRepository.saveAndFlush(entry);

            try {
                Optional<GeoLocation> location = requestNominatim(entry.getQueryCity());
                if (location.isPresent()) {
                    markFound(entry, location.get(), now);
                } else {
                    markFailed(entry, "Nominatim did not return coordinates", now);
                }
            } catch (Exception e) {
                log.warn("Ошибка геокодирования '{}': {}", entry.getQueryCity(), e.getMessage());
                markFailed(entry, truncate(e.getMessage(), 500), now);
            }
        }
    }

    private void markFound(GeocodingCacheEntry entry, GeoLocation location, LocalDateTime now) {
        entry.setLatitude(location.lat());
        entry.setLongitude(location.lng());
        entry.setStatus(GeocodingCacheEntry.FOUND);
        entry.setAttempts(entry.getAttempts() + 1);
        entry.setLastError(null);
        entry.setResolvedAt(now);
        entry.setUpdatedAt(now);
        cacheRepository.save(entry);

        List<String> keys = cityMatchKeys(entry.getCityKey());
        int users = cacheRepository.backfillUsers(keys, location.lat(), location.lng(), now);
        int posts = cacheRepository.backfillPosts(keys, location.lat(), location.lng(), now);
        log.info("Город '{}' геокодирован ({}; {}). Обновлено пользователей: {}, постов: {}",
                entry.getQueryCity(), location.lat(), location.lng(), users, posts);
    }

    private void markFailed(GeocodingCacheEntry entry, String error, LocalDateTime now) {
        int attempts = entry.getAttempts() + 1;
        entry.setAttempts(attempts);
        entry.setStatus(attempts >= Math.max(1, config.getMaxAttempts())
                ? GeocodingCacheEntry.FAILED
                : GeocodingCacheEntry.PENDING);
        entry.setLastError(truncate(error, 500));
        entry.setUpdatedAt(now);
        cacheRepository.save(entry);
    }

    private Optional<GeoLocation> requestNominatim(String cityName) {
        String url = UriComponentsBuilder.fromUriString(config.getBaseUrl())
                .queryParam("format", "json")
                .queryParam("q", cityName + ", Беларусь")
                .queryParam("limit", "1")
                .queryParam("countrycodes", config.getCountryCode())
                .build()
                .encode()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", config.getUserAgent());
        headers.set("Accept", "application/json");

        ResponseEntity<List> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), List.class);

        if (response.getBody() == null || response.getBody().isEmpty()) {
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get(0);
        Object lat = data.get("lat");
        Object lon = data.get("lon");
        if (lat == null || lon == null) {
            return Optional.empty();
        }

        return Optional.of(new GeoLocation(
                Double.parseDouble(lat.toString()),
                Double.parseDouble(lon.toString())));
    }

    static String normalizeKey(String cityName) {
        if (cityName == null) {
            return "";
        }
        String normalized = cityName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceFirst("^г\\.?\\s*", "");
        return truncate(normalized, 255);
    }

    private static List<String> cityMatchKeys(String key) {
        return List.of(key, "г. " + key, "г " + key);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public record GeoLocation(double lat, double lng) {}
}
