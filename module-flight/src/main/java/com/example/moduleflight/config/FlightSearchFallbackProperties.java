package com.example.moduleflight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.flight-search-fallback")
public record FlightSearchFallbackProperties(boolean enabled) {}
