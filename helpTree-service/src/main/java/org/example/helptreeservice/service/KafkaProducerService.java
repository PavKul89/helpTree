package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.helptreeservice.dto.HelpEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.template.default-topic:help-events}")
    private String topic;

    public void sendHelpEvent(HelpEvent event) {
        try {
            kafkaTemplate.send(topic, event.getHelpId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Событие успешно отправлено в Kafka: helpId={}, topic={}, partition={}, offset={}",
                                    event.getHelpId(),
                                    result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        } else {
                            log.error("Ошибка при отправке события в Kafka: helpId={}, error={}",
                                    event.getHelpId(), ex.getMessage(), ex);
                        }
                    });

            log.info("Отправлено событие помощи: helpId={}, type={}",
                    event.getHelpId(), event.getEventType());
        } catch (Exception e) {
            log.error("Критическая ошибка при отправке события помощи: {}", e.getMessage(), e);
        }
    }
}