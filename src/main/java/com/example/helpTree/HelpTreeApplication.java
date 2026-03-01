package com.example.helpTree;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class HelpTreeApplication {

	public static void main(String[] args) {
		SpringApplication.run(HelpTreeApplication.class, args);
		log.info("🚀 helpTree project started successfully at: http://localhost:8080");

	}

}
