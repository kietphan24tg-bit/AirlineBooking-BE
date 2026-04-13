package com.example.moduleflight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.airport-import")
public record AirportImportProperties(
    boolean enabled,
    String baseUrl,
    String cron,
    int pageSize,
    int requestTimeoutSeconds,
    int maxRetries,
    String allowedTypes,
    boolean requireIata,
    String include,
    String fallbackCity,
    String fallbackCountry,
    String fallbackTimeZone) {}
