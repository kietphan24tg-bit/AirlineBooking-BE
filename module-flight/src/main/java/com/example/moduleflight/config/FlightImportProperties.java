package com.example.moduleflight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.flight-import")
public record FlightImportProperties(
    boolean enabled,
    boolean runOnStartup,
    String baseUrl,
    String apiKey,
    String cron,
    int requestTimeoutSeconds,
    int maxRetries,
    String airlineIataCodes,
    int daysAhead) {}
