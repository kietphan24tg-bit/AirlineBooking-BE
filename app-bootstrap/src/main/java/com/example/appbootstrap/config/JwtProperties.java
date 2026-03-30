package com.example.appbootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret, int accessTokenExpirationMinutes, int refreshTokenExpirationDays) {}
