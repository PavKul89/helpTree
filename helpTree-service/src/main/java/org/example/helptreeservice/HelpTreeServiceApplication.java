package org.example.helptreeservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class HelpTreeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpTreeServiceApplication.class, args);
        log.info("🚀 Project started successfully at: http://localhost:8080");
    }

}
