package com.mentoring.bootcamp.ordermanager.api.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Basic health check controller.
 * In a real-world scenario, this helps monitoring tools
 * verify the service availability.
 */
@RestController
public class PingController {
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
