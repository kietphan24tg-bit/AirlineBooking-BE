package com.example.moduleflight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.airline-import")
public record AirlineImportProperties(
    boolean enabled,
    String baseUrl,
    String apiKey,
    String cron,
    int requestTimeoutSeconds,
    int maxRetries,
    boolean requireIata,
    String fields,
    boolean fetchCountriesEnrichment,
    String countriesFields) {}
