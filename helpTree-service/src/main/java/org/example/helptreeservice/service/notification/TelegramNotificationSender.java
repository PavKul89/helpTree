package org.example.helptreeservice.service.notification;

import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.NotificationProperties;
import org.example.helptreeservice.enums.NotificationChannel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Telegram Bot API transport. It is available only when a bot token is
 * configured; otherwise the channel is simply not offered at creation time.
 */
@Slf4j
@Component
public class TelegramNotificationSender implements NotificationSender {

    private static final int MAX_TELEGRAM_TEXT = 4096;

    private final RestTemplate restTemplate;
    private final NotificationProperties properties;

    public TelegramNotificationSender(RestTemplate restTemplate,
                                      NotificationProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public boolean isAvailable() {
        String token = properties.getTelegramBotToken();
        return token != null && !token.isBlank();
    }

    @Override
    public boolean send(NotificationDelivery delivery) {
        if (!isAvailable()) {
            return false;
        }
        if (delivery.recipient() == null || delivery.recipient().isBlank()) {
            throw new PermanentDeliveryException("У пользователя не привязан Telegram");
        }

        String text = truncate(delivery.title() + "\n\n" + delivery.body(), MAX_TELEGRAM_TEXT);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chat_id", delivery.recipient());
        request.put("text", text);
        request.put("disable_web_page_preview", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = properties.getTelegramApiUrl()
                + "/bot" + properties.getTelegramBotToken() + "/sendMessage";

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(request, headers), String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return true;
            }
            log.warn("Telegram отклонил уведомление {}: HTTP {}",
                    delivery.notificationId(), response.getStatusCode().value());
            return false;
        } catch (RestClientException e) {
            log.warn("Не удалось отправить Telegram-уведомление {}: {}",
                    delivery.notificationId(), e.getMessage());
            return false;
        }
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
