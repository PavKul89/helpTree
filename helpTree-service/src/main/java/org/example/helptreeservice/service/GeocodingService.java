package org.example.helptreeservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class GeocodingService {

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT = "HelpTree/1.0 (Java Backend)";
    private static final long MIN_REQUEST_INTERVAL = 1100;

    private final RestTemplate restTemplate;
    private final Map<String, GeoLocation> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    public GeocodingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<GeoLocation> geocodeCity(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalizedCity = cityName.toLowerCase().trim()
                .replace("г. ", "")
                .replace("г ", "");

        String cacheKey = normalizedCity;

        if (cache.containsKey(cacheKey)) {
            return Optional.ofNullable(cache.get(cacheKey));
        }

        long now = System.currentTimeMillis();
        long lastTime = lastRequestTime.get();
        long waitTime = Math.max(0, MIN_REQUEST_INTERVAL - (now - lastTime));

        if (waitTime > 0) {
            log.debug("Geocoding rate limited, skipping request for '{}'", cityName);
            return Optional.empty();
        }

        if (!lastRequestTime.compareAndSet(lastTime, now)) {
            log.debug("Geocoding concurrent request, skipping for '{}'", cityName);
            return Optional.empty();
        }

        try {
            String url = UriComponentsBuilder.fromUriString(NOMINATIM_URL)
                    .queryParam("format", "json")
                    .queryParam("q", cityName + ", Беларусь")
                    .queryParam("limit", "1")
                    .queryParam("countrycodes", "by")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    List.class
            );

            if (response.getBody() != null && !response.getBody().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.getBody().get(0);
                String lat = (String) data.get("lat");
                String lon = (String) data.get("lon");

                if (lat != null && lon != null) {
                    GeoLocation location = new GeoLocation(
                            Double.parseDouble(lat),
                            Double.parseDouble(lon)
                    );
                    cache.put(cacheKey, location);
                    log.info("Geocoded city '{}' to {}, {}", cityName, location.lat, location.lng);
                    return Optional.of(location);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to geocode city '{}': {}", cityName, e.getMessage());
        }

        return Optional.empty();
    }

    public record GeoLocation(double lat, double lng) {}
}
