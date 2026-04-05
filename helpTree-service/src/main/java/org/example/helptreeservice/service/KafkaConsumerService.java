package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.HelpEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final RatingService ratingService;

    @KafkaListener(topics = "help-events", groupId = "rating-service-group")
    public void consumeHelpEvent(HelpEvent event) {
        log.info("Получено событие помощи: helpId={}, helperId={}, receiverId={}, eventType={}", 
                event.getHelpId(), event.getHelperId(), event.getReceiverId(), event.getEventType());
        
        try {
            if (event.getEventType() == null) {
                log.warn("EventType события пуст, пропускаем");
                return;
            }
            
            switch (event.getEventType()) {
                case "HELP_CONFIRMED":
                case "HELP_COMPLETED":
                    ratingService.updateStatsAfterHelp(event.getHelperId(), event.getReceiverId(), true);
                    break;
                case "CANCELLED":
                    ratingService.updateStatsAfterHelp(event.getHelperId(), event.getReceiverId(), false);
                    break;
                default:
                    log.debug("Событие с типом {} не требует обновления рейтинга", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке события помощи: {}", e.getMessage(), e);
        }
    }
}
