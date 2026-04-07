package com.example.appbootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.refresh-cookie")
public record AuthCookieProperties(
    String name,
    String path,
    String domain,
    boolean secure,
    boolean httpOnly,
    String sameSite,
    long maxAgeSeconds) {}
