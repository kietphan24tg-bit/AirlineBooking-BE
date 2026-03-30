package com.example.appbootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.logging")
public record LoggingProperties(String requestIdHeader, boolean jsonEnabled) {}
