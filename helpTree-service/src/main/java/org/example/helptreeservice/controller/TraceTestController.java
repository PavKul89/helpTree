package org.example.helptreeservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TraceTestController {
    private static final Logger log = LoggerFactory.getLogger(TraceTestController.class);

    @GetMapping("/trace-test")
    public String test() {
        log.info("Test trace endpoint");
        return "Check logs for traceId";
    }
}
