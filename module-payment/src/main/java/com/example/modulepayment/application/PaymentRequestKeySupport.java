package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.exception.PaymentExecutionUnknownException;
import com.example.modulepayment.repository.PaymentRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class PaymentRequestKeySupport {

  private PaymentRequestKeySupport() {}

  static String normalize(String requestKey) {
    if (requestKey == null || requestKey.isBlank()) {
      return null;
    }
    String normalized = requestKey.trim();
    if (normalized.length() > 255) {
      throw new AppException(
          "PAYMENT_IDEMPOTENCY_KEY_INVALID", "Payment request key is too long", 400);
    }
    return normalized;
  }

  static PaymentEntity findExisting(PaymentRepository repository, String requestKey) {
    if (requestKey == null) {
      return null;
    }
    return repository.findByRequestKeyWithBookingForUpdate(requestKey).orElse(null);
  }

  static void validateExisting(
      PaymentEntity payment, Long bookingId, String normalizedMethod, boolean sameSettlement) {
    if (!Objects.equals(payment.getBooking().getId(), bookingId)
        || !normalizedMethod.equalsIgnoreCase(payment.getPaymentMethod())
        || !sameSettlement) {
      throw new AppException(
          "PAYMENT_IDEMPOTENCY_KEY_CONFLICT",
          "Payment request key was already used for a different payment",
          409);
    }
    if ("UNKNOWN".equalsIgnoreCase(payment.getPaymentStatus())) {
      throw new PaymentExecutionUnknownException();
    }
    if ("FAILED".equalsIgnoreCase(payment.getPaymentStatus())
        || "CANCELLED".equalsIgnoreCase(payment.getPaymentStatus())) {
      throw new AppException(
          "PAYMENT_IDEMPOTENT_REQUEST_TERMINAL",
          "The existing payment request is already in a terminal state",
          409);
    }
  }

  static CreatePaymentResult toResult(PaymentEntity payment) {
    return new CreatePaymentResult(
        new CreatePaymentResult.PaymentView(
            payment.getId(),
            payment.getBooking().getId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getPaymentMethod(),
            payment.getPaymentStatus(),
            payment.getSessionExpiresAt(),
            payment.getTransactionId(),
            payment.getQrData()),
        new CreatePaymentResult.PaymentActionView(
            payment.getType(),
            payment.getQrData(),
            payment.getQrImageUrl(),
            payment.getCheckoutUrl()));
  }

  static Map<String, String> metadata(Map<String, String> metadata, String requestKey) {
    if (requestKey == null) {
      return metadata;
    }
    Map<String, String> enriched = new LinkedHashMap<>(metadata);
    enriched.put("requestKey", requestKey);
    return Map.copyOf(enriched);
  }

  static boolean isProviderResultUnknown(RuntimeException exception) {
    if (exception instanceof AppException appException) {
      return !"PAYMENT_PROVIDER_MISCONFIGURED".equals(appException.getCode())
          && !"INVALID_PAYMENT_AMOUNT".equals(appException.getCode());
    }
    return true;
  }
}
