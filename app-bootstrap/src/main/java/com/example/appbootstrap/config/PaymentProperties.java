package com.example.appbootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
    String provider, String webhookSecret, String returnUrl, String cancelUrl) {}
