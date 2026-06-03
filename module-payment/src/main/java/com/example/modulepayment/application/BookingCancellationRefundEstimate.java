package com.example.modulepayment.application;

import org.springframework.util.StringUtils;

public record BookingCancellationRefundEstimate(String title, String message, String note) {

  private static final String PROVIDER_STRIPE = "STRIPE";
  private static final String PROVIDER_PAYOS = "PAYOS";

  public static BookingCancellationRefundEstimate forProvider(String provider) {
    String normalized = normalize(provider);
    return switch (normalized) {
      case PROVIDER_STRIPE ->
          new BookingCancellationRefundEstimate(
              "Estimated refund time",
              "Your refund is expected to arrive within 5-10 business days.",
              "Actual timing may vary depending on your card issuer.");
      case PROVIDER_PAYOS ->
          new BookingCancellationRefundEstimate(
              "Refund status",
              "Your refund request has been received and is processed 24/7.",
              "Actual arrival time depends on the receiving bank.");
      default ->
          new BookingCancellationRefundEstimate(
              "Refund status",
              "Your refund request has been received successfully.",
              "Actual arrival time depends on the payment provider and processing bank.");
    };
  }

  private static String normalize(String provider) {
    if (!StringUtils.hasText(provider)) {
      return "";
    }
    return provider.trim().toUpperCase();
  }
}
