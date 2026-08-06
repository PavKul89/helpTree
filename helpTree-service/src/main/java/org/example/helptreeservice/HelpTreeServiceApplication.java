package org.example.helptreeservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class HelpTreeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpTreeServiceApplication.class, args);
        log.info("🚀 HelpTree service started successfully at: http://localhost:8080");
        log.info("🚀 Rating service started successfully at: http://localhost:8085");
        log.info("🚀 Prometheus successfully at: http://localhost:9090");
        log.info("🚀 Grafana successfully at: http://localhost:3000");
        log.info("🚀 Jaeger successfully at: http://localhost:16686");
        log.info("🚀 MinIO successfully at: http://localhost:9001/browser/helptree");

    }

}
