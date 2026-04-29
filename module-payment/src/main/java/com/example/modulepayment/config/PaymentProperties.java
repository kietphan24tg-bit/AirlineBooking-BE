package com.example.modulepayment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Payment integration settings. PayOS webhook HMAC verification uses {@link
 * PaymentProperties.Payos#checksumKey()}, not {@code webhookSecret}.
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
    String provider,
    @Deprecated(since = "1.0", forRemoval = false) String webhookSecret,
    String returnUrl,
    String cancelUrl,
    String iconBaseUrl,
    Payos payos,
    Stripe stripe) {

  public boolean isPayosConfigured() {
    return payos != null
        && payos.clientId() != null
        && !payos.clientId().isBlank()
        && payos.apiKey() != null
        && !payos.apiKey().isBlank()
        && payos.checksumKey() != null
        && !payos.checksumKey().isBlank();
  }

  public boolean isStripeConfigured() {
    return stripe != null && stripe.secretKey() != null && !stripe.secretKey().isBlank();
  }

  public record Payos(String clientId, String apiKey, String checksumKey, String baseUrl) {}

  public record Stripe(String secretKey, String webhookSecret, String apiVersion) {}
}
