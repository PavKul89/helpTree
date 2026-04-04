package org.example.helptreeservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class GeocodingService {

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT = "HelpTree/1.0 (Java Backend)";
    private static final long MIN_REQUEST_INTERVAL = 1100;
    
    private final RestTemplate restTemplate;
    private final Map<String, GeoLocation> cache = new ConcurrentHashMap<>();
    private long lastRequestTime = 0;

    public GeocodingService() {
        this.restTemplate = new RestTemplate();
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

        try {
            Thread.sleep(Math.max(0, MIN_REQUEST_INTERVAL - (System.currentTimeMillis() - lastRequestTime)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

            lastRequestTime = System.currentTimeMillis();

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

        cache.put(cacheKey, null);
        return Optional.empty();
    }

    public record GeoLocation(double lat, double lng) {}
}
