package com.example.modulepayment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Payment integration settings. PayOS webhook HMAC verification uses {@link
 * PaymentProperties.Payos#checksumKey()}, not {@code webhookSecret}.
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
    String provider,
    @Deprecated(since = "1.0", forRemoval = false)
    String webhookSecret,
    String returnUrl,
    String cancelUrl,
    Payos payos) {

  public boolean isPayosConfigured() {
    return payos != null
        && payos.clientId() != null
        && !payos.clientId().isBlank()
        && payos.apiKey() != null
        && !payos.apiKey().isBlank()
        && payos.checksumKey() != null
        && !payos.checksumKey().isBlank();
  }

  public record Payos(String clientId, String apiKey, String checksumKey, String baseUrl) {}
}
