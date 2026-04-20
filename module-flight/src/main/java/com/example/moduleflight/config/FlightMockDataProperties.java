package com.example.moduleflight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.flight-mock")
public record FlightMockDataProperties(
    boolean enabled,
    boolean runOnStartup,
    String cron,
    boolean forceWhenTicketsExist,
    int targetFlightCount,
    int flightsPerAircraft,
    int daysSpan,
    int startDateOffsetDays,
    String excludedAirlineCodes,
    String excludedAirlineCountryCodes,
    String excludedAirportCountryCodes) {}
