package com.example.appbootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.swagger")
public record SwaggerProperties(String title, String description, String version) {}
