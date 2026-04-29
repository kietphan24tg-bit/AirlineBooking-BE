package com.example.modulepayment.gateway;

import com.example.common.exception.AppException;
import com.example.modulepayment.config.PaymentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class StripeWebhookSupport {

  private static final String PROVIDER_STRIPE = "STRIPE";

  private final PaymentProperties paymentProperties;
  private final ObjectMapper objectMapper;

  public StripeWebhookSupport(PaymentProperties paymentProperties, ObjectMapper objectMapper) {
    this.paymentProperties = paymentProperties;
    this.objectMapper = objectMapper;
  }

  public NormalizedStripeWebhookEvent parseAndVerify(String rawPayload, String signatureHeader) {
    String webhookSecret =
        paymentProperties.stripe() == null ? null : paymentProperties.stripe().webhookSecret();
    if (webhookSecret == null || webhookSecret.isBlank()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "Stripe webhook secret is not configured", 500);
    }

    Event event;
    try {
      event = Webhook.constructEvent(rawPayload, signatureHeader, webhookSecret);
    } catch (SignatureVerificationException exception) {
      throw new AppException("PAYMENT_WEBHOOK_INVALID_SIGNATURE", "Invalid signature", 401);
    }

    try {
      JsonNode root = objectMapper.readTree(rawPayload);
      JsonNode object = root.path("data").path("object");
      String eventType = event.getType();
      String sessionId = null;
      String paymentIntentId = null;
      Long paymentId = metadataPaymentId(object);
      Long amount = null;
      String currency = textOrNull(object.path("currency"));
      String paymentStatus = textOrNull(object.path("payment_status"));

      if (eventType != null && eventType.startsWith("checkout.session.")) {
        sessionId = textOrNull(object.path("id"));
        paymentIntentId = textOrNull(object.path("payment_intent"));
        amount = longOrNull(object.path("amount_total"));
      } else if ("payment_intent.payment_failed".equals(eventType)) {
        paymentIntentId = textOrNull(object.path("id"));
        amount = longOrNull(object.path("amount"));
      }

      return new NormalizedStripeWebhookEvent(
          PROVIDER_STRIPE,
          eventType,
          paymentId,
          sessionId,
          paymentIntentId,
          amount,
          currency,
          paymentStatus,
          occurredAt(event.getCreated()),
          event.getId(),
          signatureHeader,
          rawPayload);
    } catch (IOException exception) {
      throw new AppException("PAYMENT_WEBHOOK_INVALID_PAYLOAD", "Invalid Stripe payload", 400);
    }
  }

  private static Long metadataPaymentId(JsonNode object) {
    String value = textOrNull(object.path("metadata").path("paymentId"));
    if (value == null) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static String textOrNull(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null || value.isBlank() ? null : value;
  }

  private static Long longOrNull(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull() || !node.canConvertToLong()) {
      return null;
    }
    return node.asLong();
  }

  private static OffsetDateTime occurredAt(Long createdEpochSeconds) {
    long epochSeconds =
        createdEpochSeconds == null ? Instant.now().getEpochSecond() : createdEpochSeconds;
    return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
  }

  public record NormalizedStripeWebhookEvent(
      String provider,
      String eventType,
      Long paymentId,
      String sessionId,
      String paymentIntentId,
      Long amount,
      String currency,
      String paymentStatus,
      OffsetDateTime occurredAt,
      String idempotencyKey,
      String signature,
      String rawPayload) {}
}
