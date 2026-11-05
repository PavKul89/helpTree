package org.example.helptreeservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.service.GeocodingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the durable geocoding queue. The interval doubles as the Nominatim
 * rate limit, so no request path ever waits for the external service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeocodingScheduler {

    private final GeocodingService geocodingService;

    @Scheduled(fixedDelayString = "${app.geocoding.worker-interval-ms:1100}")
    public void processQueue() {
        try {
            geocodingService.processPendingBatch();
        } catch (Exception e) {
            log.error("Ошибка обработки очереди геокодирования: {}", e.getMessage(), e);
        }
    }
}
