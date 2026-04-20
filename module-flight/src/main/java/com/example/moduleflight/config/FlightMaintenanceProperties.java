package com.example.moduleflight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.flight-maintenance")
public record FlightMaintenanceProperties(
    String cron,
    boolean runOnStartup,
    boolean cleanupEnabled) {}
