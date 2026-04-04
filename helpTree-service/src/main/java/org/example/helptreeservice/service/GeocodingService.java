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

    private static final Map<String, GeoLocation> BELARUS_CITIES;

    static {
        Map<String, GeoLocation> cities = new HashMap<>();
        cities.put("гомель", new GeoLocation(52.4415, 30.9877));
        cities.put("минск", new GeoLocation(53.9045, 27.5615));
        cities.put("брест", new GeoLocation(52.0976, 23.6881));
        cities.put("гродно", new GeoLocation(53.6694, 23.8131));
        cities.put("витебск", new GeoLocation(55.1909, 30.2049));
        cities.put("могилёв", new GeoLocation(53.9008, 30.3313));
        cities.put("могилев", new GeoLocation(53.9008, 30.3313));
        cities.put("барановичи", new GeoLocation(52.1333, 26.0167));
        cities.put("пинск", new GeoLocation(52.1125, 26.1017));
        cities.put("орша", new GeoLocation(54.5007, 30.4178));
        cities.put("мозырь", new GeoLocation(51.5058, 29.0413));
        cities.put("лида", new GeoLocation(53.8917, 25.3026));
        cities.put("новополоцк", new GeoLocation(55.5317, 28.6425));
        cities.put("светлогорск", new GeoLocation(52.6333, 29.9500));
        cities.put("калинковичи", new GeoLocation(52.2431, 29.3317));
        cities.put("жлобин", new GeoLocation(52.8875, 29.7325));
        cities.put("речица", new GeoLocation(52.3567, 30.4366));
        cities.put("добруш", new GeoLocation(52.4278, 31.3208));
        cities.put("железногорск", new GeoLocation(52.2633, 29.7325));
        cities.put("лепель", new GeoLocation(54.8872, 28.6825));
        cities.put("бобруйск", new GeoLocation(53.1384, 29.2215));
        cities.put("бориславль", new GeoLocation(53.0647, 29.4625));
        cities.put("хойники", new GeoLocation(51.8833, 29.9500));
        cities.put("кричев", new GeoLocation(53.7133, 31.9500));
        cities.put("горки", new GeoLocation(54.2833, 30.9833));
        cities.put("василевичи", new GeoLocation(52.2500, 29.8333));
        cities.put("лунинец", new GeoLocation(52.2500, 26.8000));
        cities.put("петриков", new GeoLocation(52.1333, 28.5000));
        cities.put("корма", new GeoLocation(52.2167, 30.8167));
        cities.put("брагин", new GeoLocation(51.7500, 30.2667));
        cities.put("чаусы", new GeoLocation(53.8167, 31.8667));
        cities.put("россоны", new GeoLocation(55.6333, 28.8167));
        cities.put("миоры", new GeoLocation(55.6167, 27.6333));
        cities.put("дубрава", new GeoLocation(53.5333, 25.4000));
        cities.put("зэльва", new GeoLocation(53.1500, 24.8167));
        cities.put("мосты", new GeoLocation(53.4167, 24.5333));
        cities.put("волковыск", new GeoLocation(53.1667, 24.4500));
        cities.put("пружаны", new GeoLocation(52.5667, 24.4500));
        cities.put("белоозёрск", new GeoLocation(52.4500, 23.8000));
        cities.put("ивацевичи", new GeoLocation(52.7167, 25.3333));
        cities.put("клецк", new GeoLocation(53.0667, 26.6333));
        cities.put("нюнок", new GeoLocation(53.7167, 27.0500));
        cities.put("столбцы", new GeoLocation(53.4833, 26.7333));
        cities.put("марьина горка", new GeoLocation(53.5167, 28.1500));
        cities.put("пуховичи", new GeoLocation(53.5000, 28.2500));
        cities.put("березино", new GeoLocation(53.8333, 28.9833));
        cities.put("червень", new GeoLocation(53.7167, 28.4333));
        cities.put("смолевичи", new GeoLocation(54.0333, 28.0833));
        cities.put("жодино", new GeoLocation(54.1000, 28.3333));
        cities.put("молодечно", new GeoLocation(54.3167, 26.8500));
        cities.put("вилейка", new GeoLocation(54.5000, 26.9167));
        cities.put("сморгонь", new GeoLocation(54.4833, 26.4000));
        cities.put("нарочь", new GeoLocation(54.9833, 26.6833));
        cities.put("заславль", new GeoLocation(54.0000, 27.2833));
        cities.put("логойск", new GeoLocation(54.2000, 27.8500));
        cities.put("березинский", new GeoLocation(54.8333, 28.9833));
        cities.put("крупки", new GeoLocation(54.3167, 29.1333));
        cities.put("богушевск", new GeoLocation(55.0500, 29.7333));
        cities.put("сенно", new GeoLocation(55.8000, 29.8167));
        cities.put("толочин", new GeoLocation(54.6000, 29.7000));
        cities.put("чашники", new GeoLocation(54.3667, 29.1667));
        cities.put("новолукомль", new GeoLocation(55.0167, 29.2167));
        cities.put("руба", new GeoLocation(55.2000, 30.7500));
        cities.put("сураж", new GeoLocation(55.0500, 30.8167));
        cities.put("глубокое", new GeoLocation(55.1333, 27.8167));
        cities.put("поставы", new GeoLocation(55.1167, 26.8333));
        cities.put("шарковщина", new GeoLocation(55.4500, 27.4833));
        cities.put("браслав", new GeoLocation(55.6333, 27.0333));
        cities.put("докшицы", new GeoLocation(54.8833, 27.7667));
        cities.put("бешенковичи", new GeoLocation(55.0500, 28.8000));
        cities.put("верхнедвинск", new GeoLocation(55.7667, 27.9167));
        cities.put("щучин", new GeoLocation(53.6000, 24.7333));
        cities.put("берёзовка", new GeoLocation(54.2333, 25.9667));
        cities.put("краснополье", new GeoLocation(54.3167, 32.4333));
        cities.put("oshmyany", new GeoLocation(54.4167, 25.9167));
        cities.put("микашэвичи", new GeoLocation(52.7833, 27.4667));
        cities.put("любань", new GeoLocation(52.7833, 28.0000));
        cities.put("фаниполь", new GeoLocation(53.7333, 27.6667));
        cities.put("колодищи", new GeoLocation(53.9333, 27.3500));
        cities.put("узда", new GeoLocation(53.4667, 27.2167));
        cities.put("смиловичи", new GeoLocation(53.6333, 27.3000));
        cities.put("ельск", new GeoLocation(51.8167, 28.9667));
        cities.put("наровля", new GeoLocation(51.8000, 29.5167));
        cities.put("октябрьский", new GeoLocation(52.5833, 28.7333));
        cities.put("рогачев", new GeoLocation(53.0667, 30.0500));
        cities.put("буда-кошелево", new GeoLocation(52.7667, 30.5833));
        cities.put("чечерск", new GeoLocation(52.9167, 31.3167));
        cities.put("быхов", new GeoLocation(53.5167, 30.2500));
        cities.put("белыничи", new GeoLocation(54.4167, 30.8167));
        cities.put("круглое", new GeoLocation(54.6333, 29.8167));
        cities.put("мстиславль", new GeoLocation(54.0167, 31.7333));
        cities.put("житковичи", new GeoLocation(52.2167, 27.9667));
        cities.put("малорита", new GeoLocation(51.8167, 24.0167));
        cities.put("овруч", new GeoLocation(51.3167, 28.8167));
        cities.put("мядель", new GeoLocation(54.8833, 26.8167));
        cities.put("копыль", new GeoLocation(53.1500, 27.0833));
        cities.put("несвиж", new GeoLocation(53.2167, 26.6833));
        cities.put("березно", new GeoLocation(51.5667, 28.4500));
        cities.put("дрогичин", new GeoLocation(52.1833, 25.1500));
        cities.put("иваново", new GeoLocation(52.1333, 25.4833));
        cities.put("глуск", new GeoLocation(52.9000, 28.7000));
        cities.put("осиповичи", new GeoLocation(53.3000, 28.8500));
        cities.put("шклов", new GeoLocation(54.2167, 30.9167));
        cities.put("севрюки", new GeoLocation(53.8667, 32.3667));
        cities.put("хиславичи", new GeoLocation(53.9833, 32.2500));
        cities.put("пропойск", new GeoLocation(53.4000, 31.5500));
        cities.put("славгород", new GeoLocation(53.4500, 31.9000));
        cities.put("кировск", new GeoLocation(53.2833, 29.3333));
        cities.put("чериков", new GeoLocation(53.5667, 31.3667));
        cities.put("широкие", new GeoLocation(53.6500, 31.5000));
        cities.put("красная горка", new GeoLocation(53.4167, 31.6667));
        cities.put("поляна", new GeoLocation(54.3000, 30.6000));
        cities.put("победа", new GeoLocation(54.2500, 30.5000));
        cities.put("быков", new GeoLocation(53.8000, 30.3000));
        cities.put("полыковичи", new GeoLocation(53.8833, 30.4000));
        cities.put("сосновка", new GeoLocation(53.8500, 30.2500));
        cities.put("кадино", new GeoLocation(53.8167, 30.2000));
        cities.put("холм", new GeoLocation(55.0500, 31.1833));
        cities.put("холмец", new GeoLocation(54.5333, 30.2000));
        cities.put("гарна", new GeoLocation(54.4500, 30.3500));
        cities.put("халченка", new GeoLocation(54.2833, 29.1833));
        cities.put("свободный", new GeoLocation(55.2833, 28.4500));
        cities.put("куриловичи", new GeoLocation(55.0000, 28.1000));
        cities.put("копище", new GeoLocation(53.9667, 27.5667));
        cities.put("дроздово", new GeoLocation(53.8833, 27.8167));
        cities.put("боровая", new GeoLocation(53.8167, 28.0333));
        cities.put("плиса", new GeoLocation(53.5333, 28.2833));
        cities.put("батуринская", new GeoLocation(54.9833, 30.7167));
        cities.put("лёнва", new GeoLocation(54.9167, 30.5000));
        cities.put("лучеса", new GeoLocation(55.1167, 30.7333));
        cities.put("сиротино", new GeoLocation(55.2000, 30.5500));
        cities.put("шум", new GeoLocation(55.3000, 30.4000));
        cities.put("воропаево", new GeoLocation(55.3167, 30.2500));
        cities.put("дисна", new GeoLocation(55.5667, 28.2333));
        cities.put("дуниловичи", new GeoLocation(55.0167, 26.9167));
        cities.put("коммунары", new GeoLocation(55.0500, 26.6833));
        cities.put("мизгули", new GeoLocation(55.6500, 27.1500));
        cities.put("клястицы", new GeoLocation(55.7167, 28.6167));
        cities.put("валі", new GeoLocation(55.8667, 28.9500));
        cities.put("пралетарская", new GeoLocation(55.8000, 29.1333));
        cities.put("крынкі", new GeoLocation(55.8000, 29.4000));
        cities.put("казьма", new GeoLocation(55.7833, 29.6667));
        cities.put("берёза", new GeoLocation(52.5333, 24.9833));
        cities.put("городок", new GeoLocation(55.2833, 29.9833));
        cities.put("прага", new GeoLocation(52.2500, 29.8333));
        cities.put("превалока", new GeoLocation(52.2167, 27.9667));
        cities.put("перавалока", new GeoLocation(52.2167, 27.9667));
        cities.put("peravaloka", new GeoLocation(52.2167, 27.9667));
        BELARUS_CITIES = Collections.unmodifiableMap(cities);
    }
    
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

        if (BELARUS_CITIES.containsKey(normalizedCity)) {
            GeoLocation location = BELARUS_CITIES.get(normalizedCity);
            cache.put(cacheKey, location);
            log.info("Found city '{}' in Belarus cities dictionary: {}, {}", cityName, location.lat, location.lng);
            return Optional.of(location);
        }

        for (Map.Entry<String, GeoLocation> entry : BELARUS_CITIES.entrySet()) {
            if (normalizedCity.contains(entry.getKey()) || entry.getKey().contains(normalizedCity)) {
                GeoLocation location = entry.getValue();
                cache.put(cacheKey, location);
                log.info("Matched city '{}' to '{}': {}, {}", cityName, entry.getKey(), location.lat, location.lng);
                return Optional.of(location);
            }
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
