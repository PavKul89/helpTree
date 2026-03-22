package com.example.notificationservice.listener;

import com.example.notificationservice.dto.HelpEvent;
import com.example.notificationservice.service.TelegramBotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class HelpEventListener {

    private final ObjectMapper objectMapper;
    private final TelegramBotService telegramBotService;

    @Value("${app.helpTree.api-url:http://localhost:8081}")
    private String helpTreeApiUrl;

    @KafkaListener(topics = "help-events", groupId = "notification-service-group")
    public void handleHelpEvent(String message) {
        log.info("Получено событие из Kafka: {}", message);

        try {
            HelpEvent event = objectMapper.readValue(message, HelpEvent.class);

            switch (event.getEventType()) {
                case "HELP_ACCEPTED" -> {
                    String chatId = getTelegramChatId(event.getAuthorId());
                    if (chatId != null) {
                        telegramBotService.sendHelpAcceptedToAuthor(Long.parseLong(chatId), 
                            event.getPostTitle(), event.getHelperName());
                    }
                }
                case "HELP_COMPLETED" -> {
                    String chatId = getTelegramChatId(event.getAuthorId());
                    if (chatId != null) {
                        telegramBotService.sendHelpCompleted(Long.parseLong(chatId), 
                            event.getPostTitle(), event.getHelperName());
                    }
                }
                case "HELP_CONFIRMED" -> {
                    String authorChatId = getTelegramChatId(event.getAuthorId());
                    if (authorChatId != null) {
                        telegramBotService.sendHelpConfirmed(Long.parseLong(authorChatId), event.getPostTitle());
                    }
                    String helperChatId = getTelegramChatId(event.getHelperId());
                    if (helperChatId != null) {
                        telegramBotService.sendHelpConfirmed(Long.parseLong(helperChatId), event.getPostTitle());
                    }
                }
                case "HELP_CANCELLED" -> {
                    String authorChatId = getTelegramChatId(event.getAuthorId());
                    if (authorChatId != null) {
                        telegramBotService.sendHelpCancelled(Long.parseLong(authorChatId), event.getPostTitle());
                    }
                    String helperChatId = getTelegramChatId(event.getHelperId());
                    if (helperChatId != null) {
                        telegramBotService.sendHelpCancelled(Long.parseLong(helperChatId), event.getPostTitle());
                    }
                }
                default -> log.debug("Событие не требует уведомления: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки события: {}", e.getMessage(), e);
        }
    }

    private String getTelegramChatId(Long userId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = helpTreeApiUrl + "/api/users/" + userId + "/telegram-chat-id";
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.debug("Не удалось получить Telegram Chat ID для userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}
