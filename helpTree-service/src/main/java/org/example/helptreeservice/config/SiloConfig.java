package org.example.helptreeservice.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SiloConfig {

    @Bean
    public MinioClient siloClient(
            @Value("${silo.endpoint}") String endpoint,
            @Value("${silo.access-key}") String accessKey,
            @Value("${silo.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
