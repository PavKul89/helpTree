package org.example.ratingservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic helpEventsTopic() {
        return TopicBuilder.name("help-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ratingUpdatesTopic() {
        return TopicBuilder.name("rating-updates")
                .partitions(3)
                .replicas(1)
                .build();
    }
}