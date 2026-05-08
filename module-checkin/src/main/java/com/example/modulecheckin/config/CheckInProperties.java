package com.example.modulecheckin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.booking")
public record CheckInProperties(
    int pnrExpirationMinutes,
    int checkinOpenHours,
    int domesticCheckinCloseMinutes,
    int internationalCheckinCloseMinutes) {}
