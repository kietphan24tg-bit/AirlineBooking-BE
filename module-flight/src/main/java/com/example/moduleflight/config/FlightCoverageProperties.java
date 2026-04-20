package com.example.moduleflight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.flight-coverage")
public record FlightCoverageProperties(
    boolean enabled,
    int rollingDays,
    int startDateOffsetDays,
    /** Comma-separated ISO country codes (e.g. {@code VN}); only these airports join the pool. */
    String includeAirportCountryCodes,
    int maxFlightsPerRun,
    String excludedAirlineCodes,
    String excludedAirlineCountryCodes,
    String excludedAirportCountryCodes) {}
