package com.example.moduleweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
    int pnrExpirationMinutes,
    int checkinOpenHours,
    int domesticCheckinCloseMinutes,
    int internationalCheckinCloseMinutes,
    int flightChangeQuoteExpirationMinutes,
    int flightChangeCloseMinutes,
    int minimumConnectionMinutes) {}
