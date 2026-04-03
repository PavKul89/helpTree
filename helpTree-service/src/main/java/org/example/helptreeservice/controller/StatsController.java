package org.example.helptreeservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.dto.StatsDto;
import org.example.helptreeservice.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public ResponseEntity<StatsDto> getStats() {
        return ResponseEntity.ok(statsService.getStats());
    }
}
