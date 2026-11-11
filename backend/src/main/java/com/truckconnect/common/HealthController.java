package com.truckconnect.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public home-page and health endpoints.
 *
 * Before this controller existed, opening the backend's public URL
 * (GET /) or GET /api/health hit "no such route", and the global
 * exception handler turned that into a 500 error — even though the
 * app itself was running fine. These two endpoints answer 200 so
 * anyone (and Railway) can see at a glance that the service is up.
 * Both are public: SecurityConfig already permits GET /api/health
 * and any non-API request.
 */
@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
                "service", "Truck Booking",
                "status", "UP",
                "message", "Truck Booking API is running. See /api/health.");
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
