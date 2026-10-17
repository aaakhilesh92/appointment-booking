package com.org.booking.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    private static final String template = "Hello, %s!";

    @GetMapping("/greeting")
    public String greeting(@RequestParam(defaultValue = "World") String name) {
        log.info("greeting api called for {}", name);
        return template.formatted(name);
    }
}